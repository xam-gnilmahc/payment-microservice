package com.payment.microservice.controller;

import com.payment.microservice.dto.PaymentGatewayRequest;
import com.payment.microservice.dto.UserPaymentCredentialsRequest;
import com.payment.microservice.dto.UserPaymentGatewayRequest;
import com.payment.microservice.model.PaymentGateway;
import com.payment.microservice.model.PaymentGatewayStatus;
import com.payment.microservice.model.User;
import com.payment.microservice.model.UserPaymentCredentials;
import com.payment.microservice.model.UserPaymentGateway;
import com.payment.microservice.repository.PaymentGatewayRepository;
import com.payment.microservice.repository.UserPaymentCredentialsRepository;
import com.payment.microservice.repository.UserPaymentGatewayRepository;
import com.payment.microservice.traits.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-gateways")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class PaymentGatewayController {

  private final PaymentGatewayRepository paymentGatewayRepository;
  private final UserPaymentGatewayRepository userPaymentGatewayRepository;
  private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;

  private Long getUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }

  // ===== PAYMENT GATEWAY =====

  @PostMapping
  public ResponseEntity<ApiResponse<PaymentGateway>> create(
      @Valid @RequestBody PaymentGatewayRequest request) {
    Long userId = getUserId();
    PaymentGateway gateway =
        PaymentGateway.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .image(request.getImage())
            .status(
                request.getStatus() != null
                    ? PaymentGatewayStatus.fromCode(request.getStatus())
                    : PaymentGatewayStatus.ACTIVE)
            .createdBy(userId)
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(
            "Payment gateway created", 200, paymentGatewayRepository.save(gateway)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PaymentGateway>>> getAll() {
    return ResponseEntity.ok(
        ApiResponse.success(
            "Payment gateways fetched",
            200,
            paymentGatewayRepository.findAllByOrderByCreatedAtDesc()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PaymentGateway>> getById(@PathVariable Long id) {
    return paymentGatewayRepository
        .findById(id)
        .map(g -> ResponseEntity.ok(ApiResponse.success("Payment gateway fetched", 200, g)))
        .orElse(
            ResponseEntity.badRequest().body(ApiResponse.error("Payment gateway not found", 400)));
  }

  // ===== USER PAYMENT GATEWAY =====

  @PostMapping("/user")
  public ResponseEntity<ApiResponse<UserPaymentGateway>> assignToUser(
      @Valid @RequestBody UserPaymentGatewayRequest request) {
    UserPaymentGateway upg =
        UserPaymentGateway.builder()
            .userId(request.getUserId())
            .paymentGatewayId(request.getPaymentGatewayId())
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(
            "Payment gateway assigned to user", 200, userPaymentGatewayRepository.save(upg)));
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<ApiResponse<List<UserPaymentGateway>>> getUserGateways(
      @PathVariable Long userId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "User payment gateways fetched",
            200,
            userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true)));
  }

  @GetMapping("/user/{userId}/enabled")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getUserEnabledGateway(
      @PathVariable Long userId) {
    return userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true).stream()
        .findFirst()
        .map(
            upg ->
                paymentGatewayRepository
                    .findById(upg.getPaymentGatewayId())
                    .map(
                        gw -> {
                          Map<String, Object> result = new HashMap<>();
                          result.put("gatewayId", gw.getId());
                          result.put("title", gw.getTitle());
                          result.put("description", gw.getDescription());
                          result.put("image", gw.getImage());
                          result.put("userPaymentGatewayId", upg.getId());
                          userPaymentCredentialsRepository
                              .findByUserPaymentGatewaysId(upg.getId())
                              .stream()
                              .findFirst()
                              .ifPresent(cred -> result.put("publicKey", cred.getPublicKey()));
                          return ResponseEntity.ok(
                              ApiResponse.success("Gateway fetched", 200, result));
                        })
                    .orElse(
                        ResponseEntity.badRequest()
                            .body(ApiResponse.error("Gateway not found", 400))))
        .orElse(
            ResponseEntity.badRequest().body(ApiResponse.error("No enabled gateway found", 400)));
  }

  // ===== USER PAYMENT CREDENTIALS =====

  @PostMapping("/credentials")
  public ResponseEntity<ApiResponse<UserPaymentCredentials>> createCredentials(
      @Valid @RequestBody UserPaymentCredentialsRequest request) {
    UserPaymentCredentials cred =
        UserPaymentCredentials.builder()
            .userPaymentGatewaysId(request.getUserPaymentGatewaysId())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .publicKey(request.getPublicKey())
            .secretKey(request.getSecretKey())
            .webhookSecret(request.getWebhookSecret())
            .metadata(request.getMetadata())
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(
            "Credentials created", 200, userPaymentCredentialsRepository.save(cred)));
  }

  @GetMapping("/credentials/{userPaymentGatewayId}")
  public ResponseEntity<ApiResponse<List<UserPaymentCredentials>>> getCredentials(
      @PathVariable Long userPaymentGatewayId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "Credentials fetched",
            200,
            userPaymentCredentialsRepository.findByUserPaymentGatewaysId(userPaymentGatewayId)));
  }
}
