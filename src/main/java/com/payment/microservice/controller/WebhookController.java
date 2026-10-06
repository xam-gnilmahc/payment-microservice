package com.payment.microservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.microservice.model.*;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.repository.RefundLogRepository;
import com.payment.microservice.service.ReceiptEmailService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentMethod;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

  private final PaymentLogRepository paymentLogRepository;
  private final RefundLogRepository refundLogRepository;
  private final ReceiptEmailService receiptEmailService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${stripe.webhook-secret:}")
  private String webhookSecret;

  /**
   * Handles incoming Stripe webhooks. Verifies the signature, parses the event, and logs payment or refund events to the database. Sends email receipts for successful or failed payments and refunds.
   * @param payload the raw JSON payload from Stripe
   * @param sigHeader the Stripe-Signature header for signature verification
   * @return a ResponseEntity with status 200 OK if the webhook is processed successfully,
   */
  @PostMapping("/stripe")
  public ResponseEntity<String> handleStripeWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {

    log.info(
        "Webhook hit! sigHeader={}, secret={}",
        sigHeader != null && !sigHeader.isEmpty(),
        webhookSecret != null && !webhookSecret.isEmpty());

    Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      log.error("SIGNATURE FAILED: {}", e.getMessage());
      return ResponseEntity.badRequest().body("Invalid signature");
    } catch (Exception e) {
      log.error("Webhook error: {}", e.getMessage());
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }

    log.info("Webhook verified: type={}, id={}", event.getType(), event.getId());

    try {
      String type = event.getType();

      // Handle payment_intent and refund events only
      if (!type.startsWith("payment_intent.") && !type.startsWith("refund."))
        return ResponseEntity.ok("Received");

      // Parse raw JSON
      JsonNode root = objectMapper.readTree(payload);
      JsonNode data = root.path("data").path("object");

      // Handle refund events — save to refund_logs table
      if (type.startsWith("refund.")) {
        handleRefundEvent(type, data);
        return ResponseEntity.ok("Received");
      }

      String piId = data.path("id").asText(null);
      if (piId == null) return ResponseEntity.ok("Received");

      // livemode is at root level — true = live, false = test
      String paymentEnv = root.path("livemode").asBoolean(false) ? "live" : "test";

      // Extract fields from the webhook payload — no extra Stripe API calls
      JsonNode metadata = data.path("metadata");
      long amount = data.path("amount").asLong();
      String currency = data.path("currency").asText();
      String gwStatus = data.path("status").asText();
      String email = metadata.path("email").asText(null);
      String cid = metadata.path("customerId").asText(null);
      Long customerId = cid != null ? Long.parseLong(cid) : null;
      String chargeId = data.path("latest_charge").asText(null);

      // Extract payment method ID — check top level, then last_payment_error for failed events
      String pmId = data.path("payment_method").asText(null);
      if (pmId == null || pmId.isEmpty()) {
        JsonNode pmNode = data.path("last_payment_error").path("payment_method");
        if (pmNode.isTextual()) {
          pmId = pmNode.asText(null);
        } else if (pmNode.isObject()) {
          pmId = pmNode.path("id").asText(null);
        }
      }
      String paymentMethodType = resolvePaymentMethodType(pmId);

      // Route by event type and save log
      switch (type) {
          // case "payment_intent.created" ->
          //     savePaymentLog(
          //         email,
          //         customerId,
          //         piId,
          //         null,
          //         amount,
          //         currency,
          //         gwStatus,
          //         PaymentEvent.WEBHOOK_SUCCEEDED,
          //         PaymentStatus.INITIATED,
          //         "PaymentIntent created",
          //         null,
          //         paymentEnv,
          //         paymentMethodType);
        case "payment_intent.processing" ->
            savePaymentLog(
                email,
                customerId,
                piId,
                null,
                amount,
                currency,
                gwStatus,
                PaymentEvent.WEBHOOK_SUCCEEDED,
                PaymentStatus.PROCESSING,
                "Payment is processing",
                null,
                paymentEnv,
                paymentMethodType);
        case "payment_intent.succeeded" ->
            savePaymentLog(
                email,
                customerId,
                piId,
                chargeId,
                amount,
                currency,
                gwStatus,
                PaymentEvent.WEBHOOK_SUCCEEDED,
                PaymentStatus.SUCCEEDED,
                "Payment succeeded",
                null,
                paymentEnv,
                paymentMethodType);
        case "payment_intent.payment_failed" -> {
          JsonNode lastError = data.path("last_payment_error");
          String errMsg =
              lastError.isMissingNode()
                  ? "Payment failed"
                  : lastError.path("message").asText("Payment failed");
          String failCode =
              lastError.isMissingNode() ? null : lastError.path("decline_code").asText(null);
          if (failCode == null || failCode.isBlank() || "null".equals(failCode)) {
            String code = lastError.path("code").asText(null);
            failCode = code == null || code.isBlank() || "null".equals(code) ? null : code;
          }
          savePaymentLog(
              email,
              customerId,
              piId,
              null,
              amount,
              currency,
              gwStatus,
              PaymentEvent.WEBHOOK_FAILED,
              PaymentStatus.FAILED,
              errMsg,
              failCode,
              paymentEnv,
              paymentMethodType);
        }
        default -> log.info("Skipping: {}", type);
      }
    } catch (Exception e) {
      log.error("Error processing webhook {}: {}", event.getId(), e.getMessage(), e);
    }

    return ResponseEntity.ok("Received");
  }

  /** Inserts a new PaymentLog row for every webhook event. */
  private void savePaymentLog(
      String email,
      Long customerId,
      String piId,
      String chargeId,
      long amount,
      String currency,
      String gwStatus,
      PaymentEvent pEvent,
      PaymentStatus pStatus,
      String message,
      String failureCode,
      String paymentEnv,
      String paymentMethod) {

    // Without a payment method the event never became a real payment (for example the
    // intent stopped before a method was attached), so nothing is written to the log.
    if (paymentMethod == null || paymentMethod.isBlank()) {
      log.warn("Skipped payment log for pi={} event={}: no payment method", piId, pEvent);
      return;
    }

    PaymentLog logEntry =
        PaymentLog.builder()
            .email(email)
            .customerId(customerId)
            .gateway("STRIPE")
            .event(pEvent)
            .status(pStatus)
            .transactionId(piId)
            .chargeId(chargeId)
            .amount(toDollars(amount))
            .currency(currency)
            .gatewayStatus(gwStatus)
            .failureCode(failureCode)
            .message(message)
            .paymentEnvironment(paymentEnv)
            .paymentMethod(paymentMethod)
            .build();

    paymentLogRepository.save(logEntry);
    log.info("Saved: id={}, pi={}, pm={}", logEntry.getId(), piId, paymentMethod);

    // Customer receipt on success or failure
<<<<<<< HEAD
    // Turned off on purpose: no email is sent for any webhook event any more.
=======
>>>>>>> 3040f70c227f46319dab0255a8b92a3d647cc54a
    // if (pStatus == PaymentStatus.SUCCEEDED || pStatus == PaymentStatus.FAILED) {
    //   try {
    //     boolean sent = receiptEmailService.sendPaymentReceipt(logEntry);
    //     log.info("Payment receipt for log id={}: sent={}", logEntry.getId(), sent);
    //   } catch (Exception e) {
    //     log.warn("Payment receipt failed for log id={}: {}", logEntry.getId(), e.getMessage());
    //   }
    // }
  }

  /** Calls Stripe API to resolve payment method ID to type (card, link, etc.) */
  private String resolvePaymentMethodType(String paymentMethodId) {
    if (paymentMethodId == null || paymentMethodId.isEmpty()) return null;
    try {
      PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
      String type = pm.getType();
      if ("card".equals(type) && pm.getCard() != null && pm.getCard().getWallet() != null) {
        String walletType = pm.getCard().getWallet().getType();
        if (walletType != null) return walletType.toLowerCase();
      }
      return type;
    } catch (Exception e) {
      log.warn("Failed to retrieve payment method {}: {}", paymentMethodId, e.getMessage());
      return null;
    }
  }

  /** Converts Stripe cents to dollars for storage. */
  private BigDecimal toDollars(Long cents) {
    if (cents == null) return BigDecimal.ZERO;
    return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /**
   * Handles refund events from Stripe webhooks. Extracts relevant fields, determines status and message, saves a new RefundLog entry, and sends email receipts for terminal events (success or failure).
   * @param type the Stripe event type (e.g., refund.created, refund.updated, refund.failed)
   * @param data the JSON node containing the refund object data
   */
  private void handleRefundEvent(String type, JsonNode data) {
    String refundId = data.path("id").asText(null);
    String chargeId = data.path("charge").asText(null);
    String piId = data.path("payment_intent").asText(null);
    long amount = data.path("amount").asLong();
    String currency = data.path("currency").asText();
    String status = data.path("status").asText();

    // Safe parsing for customerId
    String cid = data.path("metadata").path("customerId").asText(null);
    Long customerId = null;
    if (cid != null && !cid.isBlank()) {
      try {
        customerId = Long.parseLong(cid);
      } catch (NumberFormatException e) {
        log.warn("Non-numeric customerId metadata: {}", cid);
      }
    }

    // Extract card_reference from destination_details
    String cardReference = null;
    JsonNode destDetails = data.path("destination_details");
    if (!destDetails.isMissingNode()) {
      JsonNode card = destDetails.path("card");
      if (!card.isMissingNode()) {
        cardReference = card.path("reference").asText(null);
      }
    }

    String failureReason = data.path("failure_reason").asText("unknown");

    // Determine status code and precise messaging
    String statusCode;
    String message;
    String refStr =
        (cardReference != null && !cardReference.isEmpty())
            ? " (Bank Tracking Ref: " + cardReference + ")"
            : "";

    switch (status) {
      case "succeeded" -> {
        statusCode = "1";
        if ("refund.created".equals(type)) {
          message = "Your refund has been started! The money was deducted from our account and is on its way to your bank" + refStr + ".";
        } else {
          // refund.updated - succeeded
          message = "Refund complete! The money has been successfully sent to your bank. It usually takes 5 to 10 days to show up on your statement" + refStr + ".";
        }
      }
      case "pending" -> {
        statusCode = "0";
        message = "Your refund is being processed. The funds are currently held and waiting to clear with the banking networks" + refStr + ".";
      }
      case "requires_action" -> {
        statusCode = "0";
        message = "We need additional information or action from you before this refund can be sent out.";
      }
      case "canceled" -> {
        statusCode = "2";
        message = "This refund was canceled before it could complete. No money was moved.";
      }
      case "failed" -> {
        statusCode = "2";
        message = "The refund failed because your bank turned it down (" + failureReason + "). The money was returned to our system.";
      }
      default -> {
        statusCode = "0";
        message = "We are checking on the status of your refund (" + status + "). Please check back soon.";
      }
    }

    // Always insert a new row per event
    String email = null;
    if (chargeId != null && !chargeId.isBlank()) {
      email = paymentLogRepository.findByChargeId(chargeId).map(PaymentLog::getEmail).orElse(null);
    }

    RefundLog refundLog =
        RefundLog.builder()
            .transactionId(piId)
            .chargeId(chargeId)
            .refundId(refundId)
            .amount(toDollars(amount))
            .currency(currency)
            .customerId(customerId)
            .email(email)
            .cardReference(cardReference)
            .status(statusCode)
            .message(message)
            .build();

    refundLogRepository.save(refundLog);
    log.info("Refund log saved: refundId={}, status={}, type={}", refundId, statusCode, type);

    // Customer refund receipt — turned off on purpose, same as the payment receipt above.
    // boolean terminal = "1".equals(statusCode) || "2".equals(statusCode);
    //
    // boolean shouldSendEmail = ("refund.updated".equals(type) && "1".equals(statusCode)) || "refund.failed".equals(type);
    //
    // if (terminal && shouldSendEmail) {
    //   try {
    //     boolean sent = receiptEmailService.sendRefundReceipt(refundLog);
    //     log.info("Refund receipt for log id={}: sent={}", refundLog.getId(), sent);
    //   } catch (Exception e) {
    //     log.warn("Refund receipt failed for log id={}: {}", refundLog.getId(), e.getMessage());
    //   }
    // }
  }

}
