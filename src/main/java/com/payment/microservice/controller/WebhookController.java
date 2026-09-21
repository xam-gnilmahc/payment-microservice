package com.payment.microservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.microservice.model.*;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.repository.RefundLogRepository;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${stripe.webhook-secret:}")
  private String webhookSecret;

  /**
   * Main webhook endpoint — receives all Stripe webhook events. Verifies signature, parses raw
   * JSON, and saves payment_intent events to payment_logs.
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

      // Only handle payment_intent and refund events
      if (!type.startsWith("payment_intent.") && !type.startsWith("refund.")) {
        log.info("Ignoring: {}", type);
        return ResponseEntity.ok("Received");
      }

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
          String failCode = lastError.isMissingNode() ? null : lastError.path("code").asText(null);
          String declineCode =
              lastError.isMissingNode() ? null : lastError.path("decline_code").asText(null);
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
              declineCode != null ? declineCode : errMsg,
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

  /** Builds a PaymentLog from webhook data and saves to database. */
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

  /** Handles refund.created, refund.updated, refund.failed events. */
  private void handleRefundEvent(String type, JsonNode data) {
    String refundId = data.path("id").asText(null);
    String chargeId = data.path("charge").asText(null);
    String piId = data.path("payment_intent").asText(null);
    long amount = data.path("amount").asLong();
    String currency = data.path("currency").asText();
    String reason = data.path("reason").asText(null);
    String cid = data.path("metadata").path("customerId").asText(null);
    Long customerId = cid != null ? Long.parseLong(cid) : null;
    String status = data.path("status").asText();

    // Extract card_reference from destination_details
    String cardReference = null;
    JsonNode destDetails = data.path("destination_details");
    if (!destDetails.isMissingNode()) {
      JsonNode card = destDetails.path("card");
      if (!card.isMissingNode()) {
        cardReference = card.path("reference").asText(null);
      }
    }

    // Determine status code by event type
    String statusCode;
    String message;
    switch (type) {
      case "refund.created":
        statusCode = "0";
        message = "Refund pending";
        break;
      case "refund.updated":
        if ("succeeded".equals(status)) {
          statusCode = "1";
          message =
              "Refund issued - money is on its way, takes up to 10 business days to appear on statement";
        } else if ("failed".equals(status)) {
          statusCode = "2";
          message = data.path("failure_reason").asText("Refund failed");
        } else {
          statusCode = "0";
          message = "Refund " + status;
        }
        break;
      case "refund.failed":
        statusCode = "2";
        message = data.path("failure_reason").asText("Refund failed");
        break;
      default:
        statusCode = "0";
        message = "Refund " + status;
        break;
    }

    // Always insert new row
    RefundLog refundLog =
        RefundLog.builder()
            .transactionId(piId)
            .chargeId(chargeId)
            .refundId(refundId)
            .amount(toDollars(amount))
            .currency(currency)
            .customerId(customerId)
            .cardReference(cardReference)
            .status(statusCode)
            .message(message)
            .build();

    refundLogRepository.save(refundLog);
    log.info(
        "Refund log saved: refundId={}, status={}, cardReference={}",
        refundId,
        statusCode,
        cardReference);
  }
}
