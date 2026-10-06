package com.payment.microservice.controller;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.dto.RefundRequest;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.RefundLog;
import com.payment.microservice.model.User;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.repository.RefundLogRepository;
import com.payment.microservice.service.PaymentGatewayService;
import com.payment.microservice.service.PaymentService;
import com.payment.microservice.traits.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.payment.microservice.traits.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentLogRepository paymentLogRepository;
  private final RefundLogRepository refundLogRepository;
  
  /**
   * Create a payment intent for the current user. The request body must contain the payment details. The response will contain the payment intent ID and client secret.
   * @param request the payment request containing amount, currency, and other details
   * @return a ResponseEntity containing the ApiResponse with payment intent details or an error message
   */
  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, String>>> createPayment(
      @Valid @RequestBody PaymentRequest request) {
    try {
      Long customerId = CurrentUser.id();
      request.setCustomerId(customerId);
      PaymentGatewayService gateway = paymentService.getService(customerId);
      Map<String, String> result = gateway.createPaymentIntent(request);
      return ResponseEntity.ok(ApiResponse.success("Payment initiated", 200, result));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Payment failed: " + e.getMessage(), 400));
    }
  }
  
  /**
   * Confirm a payment intent for the current user. The request body must contain the payment intent ID and optionally the payment method. The response will contain the payment status.
   * @param body a map containing the payment intent ID and optionally the payment method
   * @return a ResponseEntity containing the ApiResponse with payment status or an error message
   */
  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Map<String, String>>> confirmPayment(
      @RequestBody Map<String, Object> body) {
    try {
      String transactionId = (String) body.get("paymentIntentId");
      Long customerId = CurrentUser.id();
      String paymentMethod =
          body.get("paymentMethod") != null ? body.get("paymentMethod").toString() : null;

      PaymentGatewayService gateway = paymentService.getService(customerId);
      Map<String, String> result =
          gateway.getPaymentIntentStatus(transactionId, customerId, paymentMethod);
      return ResponseEntity.ok(ApiResponse.success("Payment confirmed", 200, result));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Payment confirmation failed: " + e.getMessage(), 400));
    }
  }
  
  /**
   * Refund a payment for the current user. The request body must contain the charge ID and optionally a reason for the refund. The response will contain the refund ID and status.
   * @param request the refund request containing charge ID and optionally a reason
   * @return a ResponseEntity containing the ApiResponse with refund details or an error message
   */
  @PostMapping("/refund")
  public ResponseEntity<ApiResponse<Map<String, String>>> refundPayment(
      @Valid @RequestBody RefundRequest request) {
    try {
      // same here: the session says who is asking, and an admin may refund anybody's charge
      Long customerId = CurrentUser.id();
      if (!CurrentUser.isAdmin()) {
        boolean ownsCharge =
            paymentLogRepository
                .findByChargeId(request.getChargeId())
                .map(l -> customerId.equals(l.getCustomerId()))
                .orElse(false);
        if (!ownsCharge) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(ApiResponse.error("This payment does not belong to you", 403));
        }
      }
      request.setCustomerId(customerId);
      PaymentGatewayService gateway = paymentService.getService(customerId);
      Map<String, String> result =
          gateway.refundPayment(request.getChargeId(), customerId, request.getReason());

      // Save refund ID to payment log
      paymentLogRepository
          .findByChargeId(request.getChargeId())
          .ifPresent(
              log -> {
                log.setRefundId(result.get("refundId"));
                paymentLogRepository.save(log);
              });

      return ResponseEntity.ok(ApiResponse.success("Refund initiated", 200, result));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Refund failed: " + e.getMessage(), 400));
    }
  }
  
  /**
   * Get payment logs for the current user. Admins can see all logs, while regular users can only see their own logs. Supports pagination with page and size parameters.
   * @param page the page number (default is 0)
   * @param size the page size (default is 50)
   * @return a ResponseEntity containing the ApiResponse with payment logs and pagination info or an
   */
  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentLogs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    try {
      User user = CurrentUser.get();
      boolean isSuperAdmin = CurrentUser.isAdmin();

      Page<PaymentLog> logPage =
          isSuperAdmin
              ? paymentLogRepository.findAllByIdDesc(PageRequest.of(page, size))
              : paymentLogRepository.findByCustomerIdOrderByIdDesc(
                  user.getId(), PageRequest.of(page, size));

      Map<String, Object> response =
          Map.of(
              "logs", logPage.getContent(),
              "currentPage", logPage.getNumber(),
              "totalPages", logPage.getTotalPages(),
              "totalElements", logPage.getTotalElements());

      return ResponseEntity.ok(ApiResponse.success("Payment logs fetched", 200, response));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to fetch payment logs: " + e.getMessage(), 400));
    }
  }
  
  /**
   * Get a summary of payments for the current user. Admins can see a summary of all payments, while regular users can only see their own payments. The summary includes total payments, succeeded payments, failed payments, and total volume.
   * @return a ResponseEntity containing the ApiResponse with payment summary or an error message
   */
  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentSummary() {
    try {
      User user = CurrentUser.get();
      boolean isSuperAdmin = CurrentUser.isAdmin();
      Long userId = user.getId();

      long total =
          isSuperAdmin ? paymentLogRepository.count() : paymentLogRepository.countByCustomerId(userId);
      long succeeded =
          isSuperAdmin
              ? paymentLogRepository.countByStatus(com.payment.microservice.model.PaymentStatus.SUCCEEDED)
              : paymentLogRepository.countByCustomerIdAndStatus(
                  userId, com.payment.microservice.model.PaymentStatus.SUCCEEDED);
      long failed =
          isSuperAdmin
              ? paymentLogRepository.countByStatus(com.payment.microservice.model.PaymentStatus.FAILED)
              : paymentLogRepository.countByCustomerIdAndStatus(
                  userId, com.payment.microservice.model.PaymentStatus.FAILED);
      java.math.BigDecimal volume =
          isSuperAdmin
              ? paymentLogRepository.sumAmountByStatus(
                  com.payment.microservice.model.PaymentStatus.SUCCEEDED)
              : paymentLogRepository.sumAmountByCustomerIdAndStatus(
                  userId, com.payment.microservice.model.PaymentStatus.SUCCEEDED);

      Map<String, Object> summary =
          Map.of(
              "total", total,
              "succeeded", succeeded,
              "failed", failed,
              "volume", volume == null ? java.math.BigDecimal.ZERO : volume);

      return ResponseEntity.ok(ApiResponse.success("Payment summary fetched", 200, summary));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to fetch payment summary: " + e.getMessage(), 400));
    }
  }
  
  /**
   * Get refund logs for a specific charge ID. Admins can see all refund logs, while regular users can only see their own refund logs. The charge ID is passed as a query parameter.
   * @param chargeId the charge ID for which to fetch refund logs
   * @return a ResponseEntity containing the ApiResponse with refund logs or an error message
   */
  @GetMapping("/refund-logs")
  public ResponseEntity<ApiResponse<java.util.List<RefundLog>>> getRefundLogs(
      @RequestParam String chargeId) {
    try {
      User user = CurrentUser.get();
      boolean isSuperAdmin = CurrentUser.isAdmin();

      if (!isSuperAdmin) {
        boolean ownsCharge =
            paymentLogRepository.findByChargeId(chargeId).map(l -> user.getId().equals(l.getCustomerId())).orElse(false);
        if (!ownsCharge) {
          return ResponseEntity.badRequest().body(ApiResponse.error("Refund logs not found", 404));
        }
      }

      java.util.List<RefundLog> logs = refundLogRepository.findByChargeId(chargeId);
      return ResponseEntity.ok(ApiResponse.success("Refund logs fetched", 200, logs));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to fetch refund logs: " + e.getMessage(), 400));
    }
  }
}
