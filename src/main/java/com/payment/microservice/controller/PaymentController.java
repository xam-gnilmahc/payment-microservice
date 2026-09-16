package com.payment.microservice.controller;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.service.PaymentService;
import com.payment.microservice.traits.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
            Map<String, String> result = paymentService.initiatePayment(request);
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
            String paymentIntentId = (String) body.get("paymentIntentId");
            Long customerId = Long.valueOf(body.get("customerId").toString());
            Long gatewayId = body.get("gatewayId") != null ? Long.valueOf(body.get("gatewayId").toString()) : null;
            String paymentMethod = body.get("paymentMethod") != null ? body.get("paymentMethod").toString() : null;

            Map<String, String> result = paymentService.confirmPayment(paymentIntentId, customerId, gatewayId, paymentMethod);
            return ResponseEntity.ok(ApiResponse.success("Payment confirmed", 200, result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Payment confirmation failed: " + e.getMessage(), 400));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentLog>>> getPaymentLogs() {
        try {
            List<PaymentLog> logs = paymentLogRepository.findAllByOrderByCreatedAtDesc();
            return ResponseEntity.ok(ApiResponse.success("Payment logs fetched", 200, logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch payment logs: " + e.getMessage(), 400));
        }
    }
}
