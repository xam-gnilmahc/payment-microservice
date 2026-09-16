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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<ApiResponse<PaymentGateway>> create(@Valid @RequestBody PaymentGatewayRequest request) {
        Long userId = getUserId();
        PaymentGateway gateway = PaymentGateway.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .image(request.getImage())
                .status(request.getStatus() != null ? PaymentGatewayStatus.fromCode(request.getStatus()) : PaymentGatewayStatus.ACTIVE)
                .createdBy(userId)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Payment gateway created", 200, paymentGatewayRepository.save(gateway)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentGateway>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Payment gateways fetched", 200, paymentGatewayRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentGateway>> getById(@PathVariable Long id) {
        return paymentGatewayRepository.findById(id)
                .map(g -> ResponseEntity.ok(ApiResponse.success("Payment gateway fetched", 200, g)))
                .orElse(ResponseEntity.badRequest().body(ApiResponse.error("Payment gateway not found", 400)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentGateway>> update(@PathVariable Long id, @RequestBody PaymentGatewayRequest request) {
        Long userId = getUserId();
        return paymentGatewayRepository.findById(id).map(g -> {
            g.setTitle(request.getTitle());
            g.setDescription(request.getDescription());
            g.setImage(request.getImage());
            if (request.getStatus() != null) g.setStatus(PaymentGatewayStatus.fromCode(request.getStatus()));
            g.setUpdatedBy(userId);
            return ResponseEntity.ok(ApiResponse.success("Payment gateway updated", 200, paymentGatewayRepository.save(g)));
        }).orElse(ResponseEntity.badRequest().body(ApiResponse.error("Payment gateway not found", 400)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        if (paymentGatewayRepository.existsById(id)) {
            paymentGatewayRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("Payment gateway deleted", 200, "Deleted"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Payment gateway not found", 400));
    }

    // ===== USER PAYMENT GATEWAY =====

    @PostMapping("/user")
    public ResponseEntity<ApiResponse<UserPaymentGateway>> assignToUser(@Valid @RequestBody UserPaymentGatewayRequest request) {
        UserPaymentGateway upg = UserPaymentGateway.builder()
                .userId(request.getUserId())
                .paymentGatewayId(request.getPaymentGatewayId())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Payment gateway assigned to user", 200, userPaymentGatewayRepository.save(upg)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserPaymentGateway>>> getUserGateways(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User payment gateways fetched", 200, userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true)));
    }

    @PutMapping("/user/{id}")
    public ResponseEntity<ApiResponse<UserPaymentGateway>> updateUserGateway(@PathVariable Long id, @RequestBody UserPaymentGatewayRequest request) {
        return userPaymentGatewayRepository.findById(id).map(upg -> {
            if (request.getEnabled() != null) upg.setEnabled(request.getEnabled());
            return ResponseEntity.ok(ApiResponse.success("User payment gateway updated", 200, userPaymentGatewayRepository.save(upg)));
        }).orElse(ResponseEntity.badRequest().body(ApiResponse.error("User payment gateway not found", 400)));
    }

    @DeleteMapping("/user/{id}")
    public ResponseEntity<ApiResponse<String>> deleteUserGateway(@PathVariable Long id) {
        if (userPaymentGatewayRepository.existsById(id)) {
            userPaymentGatewayRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("User payment gateway deleted", 200, "Deleted"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("User payment gateway not found", 400));
    }

    @GetMapping("/user/{userId}/details")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getUserGatewayDetails(@PathVariable Long userId) {
        List<UserPaymentGateway> userGateways = userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (UserPaymentGateway upg : userGateways) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("userGatewayId", upg.getId());
            item.put("enabled", upg.getEnabled());
            paymentGatewayRepository.findById(upg.getPaymentGatewayId()).ifPresent(gw -> {
                item.put("gatewayId", gw.getId());
                item.put("title", gw.getTitle());
                item.put("description", gw.getDescription());
                item.put("image", gw.getImage());
            });
            List<UserPaymentCredentials> creds = userPaymentCredentialsRepository.findByUserPaymentGatewaysId(upg.getId());
            item.put("hasCredentials", !creds.isEmpty());
            result.add(item);
        }
        return ResponseEntity.ok(ApiResponse.success("User gateway details fetched", 200, result));
    }

    // ===== USER PAYMENT CREDENTIALS =====

    @PostMapping("/credentials")
    public ResponseEntity<ApiResponse<UserPaymentCredentials>> createCredentials(@Valid @RequestBody UserPaymentCredentialsRequest request) {
        UserPaymentCredentials cred = UserPaymentCredentials.builder()
                .userPaymentGatewaysId(request.getUserPaymentGatewaysId())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .publicKey(request.getPublicKey())
                .secretKey(request.getSecretKey())
                .webhookSecret(request.getWebhookSecret())
                .metadata(request.getMetadata())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Credentials created", 200, userPaymentCredentialsRepository.save(cred)));
    }

    @GetMapping("/credentials/{userPaymentGatewayId}")
    public ResponseEntity<ApiResponse<List<UserPaymentCredentials>>> getCredentials(@PathVariable Long userPaymentGatewayId) {
        return ResponseEntity.ok(ApiResponse.success("Credentials fetched", 200, userPaymentCredentialsRepository.findByUserPaymentGatewaysId(userPaymentGatewayId)));
    }

    @PutMapping("/credentials/{id}")
    public ResponseEntity<ApiResponse<UserPaymentCredentials>> updateCredentials(@PathVariable Long id, @RequestBody UserPaymentCredentialsRequest request) {
        return userPaymentCredentialsRepository.findById(id).map(cred -> {
            if (request.getIsActive() != null) cred.setIsActive(request.getIsActive());
            cred.setPublicKey(request.getPublicKey());
            cred.setSecretKey(request.getSecretKey());
            cred.setWebhookSecret(request.getWebhookSecret());
            cred.setMetadata(request.getMetadata());
            return ResponseEntity.ok(ApiResponse.success("Credentials updated", 200, userPaymentCredentialsRepository.save(cred)));
        }).orElse(ResponseEntity.badRequest().body(ApiResponse.error("Credentials not found", 400)));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<ApiResponse<String>> deleteCredentials(@PathVariable Long id) {
        if (userPaymentCredentialsRepository.existsById(id)) {
            userPaymentCredentialsRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("Credentials deleted", 200, "Deleted"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Credentials not found", 400));
    }
}
