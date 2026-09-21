package com.payment.microservice.controller;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.repository.PaymentLogRepository;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentLogRepository paymentLogRepository;

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

  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentLogs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    try {
      Page<PaymentLog> logPage =
          paymentLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

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
}
