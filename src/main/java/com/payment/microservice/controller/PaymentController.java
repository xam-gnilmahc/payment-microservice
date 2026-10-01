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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentLogRepository paymentLogRepository;
  private final RefundLogRepository refundLogRepository;

  private boolean isSuperAdmin() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (!(principal instanceof User user)) return false;
    String flag = user.getIsSuperAdmin();
    return flag != null && ("1".equals(flag) || "true".equalsIgnoreCase(flag));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, String>>> createPayment(
      @Valid @RequestBody PaymentRequest request) {
    try {
      PaymentGatewayService gateway = paymentService.getService(request.getCustomerId());
      Map<String, String> result = gateway.createPaymentIntent(request);
      return ResponseEntity.ok(ApiResponse.success("Payment initiated", 200, result));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Payment failed: " + e.getMessage(), 400));
    }
  }

  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Map<String, String>>> confirmPayment(
      @RequestBody Map<String, Object> body) {
    try {
      String transactionId = (String) body.get("paymentIntentId");
      Long customerId = Long.valueOf(body.get("customerId").toString());
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

  @PostMapping("/refund")
  public ResponseEntity<ApiResponse<Map<String, String>>> refundPayment(
      @Valid @RequestBody RefundRequest request) {
    try {
      PaymentGatewayService gateway = paymentService.getService(request.getCustomerId());
      Map<String, String> result =
          gateway.refundPayment(
              request.getChargeId(), request.getCustomerId(), request.getReason());

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

  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentLogs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    try {
      User user =
          (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      boolean isSuperAdmin = isSuperAdmin();

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

  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentSummary() {
    try {
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      boolean isSuperAdmin = isSuperAdmin();
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

  @GetMapping("/refund-logs")
  public ResponseEntity<ApiResponse<java.util.List<RefundLog>>> getRefundLogs(
      @RequestParam String chargeId) {
    try {
      User user =
          (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      boolean isSuperAdmin = isSuperAdmin();

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
