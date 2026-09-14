package com.payment.microservice.controller;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.service.PaymentService;
import com.payment.microservice.traits.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // This endpoint receives payment requests from the frontend.
    // It validates the request data, then calls PaymentService to process the payment.
    // If payment succeeds, it returns the transaction details.
    // If payment fails, it returns the error message.
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        try {
            Map<String, String> result = paymentService.processPayment(request);
            return ResponseEntity.ok(ApiResponse.success("Payment successful", 200, result));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Payment failed: " + e.getMessage(), 400));
        }
    }
}
