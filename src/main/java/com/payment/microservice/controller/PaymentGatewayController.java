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
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.payment.microservice.traits.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-gateways")
@RequiredArgsConstructor
public class PaymentGatewayController {

  private final PaymentGatewayRepository paymentGatewayRepository;
  private final UserPaymentGatewayRepository userPaymentGatewayRepository;
  private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;

  /**
   * Create a new payment gateway. The request body should contain title, description, image, and optional status. Returns 200 OK with the created gateway, or 400 Bad Request if validation fails.
   */
  @PostMapping
  public ResponseEntity<ApiResponse<PaymentGateway>> create(
      @Valid @RequestBody PaymentGatewayRequest request) {
    Long userId = CurrentUser.id();
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
  
  /**
   * List all payment gateways, ordered by creation date descending. Returns 200 OK with the list of gateways.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<PaymentGateway>>> getAll() {
    return ResponseEntity.ok(
        ApiResponse.success(
            "Payment gateways fetched",
            200,
            paymentGatewayRepository.findAllByOrderByCreatedAtDesc()));
  }
  
  /**
   * Get a payment gateway by ID. Returns 200 OK with the gateway if found, or 400 Bad Request if not found.
   */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PaymentGateway>> getById(@PathVariable Long id) {
    return paymentGatewayRepository
        .findById(id)
        .map(g -> ResponseEntity.ok(ApiResponse.success("Payment gateway fetched", 200, g)))
        .orElse(
            ResponseEntity.badRequest().body(ApiResponse.error("Payment gateway not found", 400)));
  }

  /**
   * Assign a payment gateway to a user. The request body should contain userId, paymentGatewayId, and optional enabled flag. Returns 200 OK with the created UserPaymentGateway, or 400 Bad Request if validation fails.
   */
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
  
  /**
   * Get all payment gateways assigned to a user. Returns 200 OK with the list of UserPaymentGateway, or 400 Bad Request if the user has no gateways.
   */
  @GetMapping("/user/{userId}")
  public ResponseEntity<ApiResponse<List<UserPaymentGateway>>> getUserGateways(
      @PathVariable Long userId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "User payment gateways fetched",
            200,
            userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true)));
  }
  
  /**
   * Get the enabled payment gateway for a user. If the caller is not the user or an admin, returns 403 Forbidden. If the user has no enabled gateway, returns 400 Bad Request. Otherwise, returns 200 OK with the gateway details and public key if available.
   */
  @GetMapping("/user/{userId}/enabled")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getUserEnabledGateway(
      @PathVariable Long userId) {
    // ignore the id in the url when it is not our own, unless we are an admin
    User caller = CurrentUser.get();
    if (!caller.getId().equals(userId) && !"1".equals(caller.getIsSuperAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(ApiResponse.error("This account is not yours", 403));
    }

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

  /**
   * Create credentials for a user's payment gateway. The request body should contain userPaymentGatewaysId, publicKey, secretKey, webhookSecret, metadata, and optional isActive flag. Returns 200 OK with the created UserPaymentCredentials, or 400 Bad Request if validation fails.
   */
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
  
  /**
   * Get all credentials for a user's payment gateway. Returns 200 OK with the list of UserPaymentCredentials, or 400 Bad Request if the user has no credentials.
   */
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
