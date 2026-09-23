package com.payment.microservice.controller;

import com.payment.microservice.model.PaymentGateway;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
import com.payment.microservice.model.RefundLog;
import com.payment.microservice.model.User;
import com.payment.microservice.model.UserPaymentCredentials;
import com.payment.microservice.model.UserPaymentGateway;
import com.payment.microservice.repository.PaymentGatewayRepository;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.repository.RefundLogRepository;
import com.payment.microservice.repository.UserPaymentCredentialsRepository;
import com.payment.microservice.repository.UserPaymentGatewayRepository;
import com.payment.microservice.repository.UserRepository;
import com.payment.microservice.traits.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

  private final UserRepository userRepository;
  private final PaymentGatewayRepository paymentGatewayRepository;
  private final UserPaymentGatewayRepository userPaymentGatewayRepository;
  private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;
  private final RefundLogRepository refundLogRepository;
  private final PaymentLogRepository paymentLogRepository;

  @GetMapping("/users")
  public ResponseEntity<ApiResponse<Iterable<User>>> getUsers() {
    return ResponseEntity.ok(ApiResponse.success("Users fetched", 200, userRepository.findAll()));
  }

  @GetMapping("/gateways")
  public ResponseEntity<ApiResponse<Iterable<PaymentGateway>>> getGateways() {
    return ResponseEntity.ok(
        ApiResponse.success("Gateways fetched", 200, paymentGatewayRepository.findAll()));
  }

  @PostMapping("/assign-gateway")
  public ResponseEntity<ApiResponse<String>> assignGateway(@RequestBody Map<String, Object> body) {
    try {
      Long userId = Long.valueOf(body.get("userId").toString());
      Long gatewayId = Long.valueOf(body.get("gatewayId").toString());

      // Check if already assigned
      var existing =
          userPaymentGatewayRepository.findByUserIdAndPaymentGatewayId(userId, gatewayId);
      if (existing.isPresent()) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Gateway already assigned to this user", 400));
      }

      boolean hasEnabled =
          userPaymentGatewayRepository.findByUserIdAndEnabled(userId, true).size() > 0;
      UserPaymentGateway upg =
          UserPaymentGateway.builder()
              .userId(userId)
              .paymentGatewayId(gatewayId)
              .enabled(!hasEnabled)
              .build();
      userPaymentGatewayRepository.save(upg);
      return ResponseEntity.ok(ApiResponse.success("Gateway assigned", 200, "OK"));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to assign gateway: " + e.getMessage(), 400));
    }
  }

  @GetMapping("/refund-logs")
  public ResponseEntity<ApiResponse<Iterable<RefundLog>>> getRefundLogs() {
    return ResponseEntity.ok(
        ApiResponse.success("Refund logs fetched", 200, refundLogRepository.findAll()));
  }

  @GetMapping("/user/{userId}/payment-logs")
  public ResponseEntity<ApiResponse<List<PaymentLog>>> getUserPaymentLogs(
      @PathVariable Long userId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "User payment logs fetched", 200, paymentLogRepository.findByCustomerId(userId)));
  }

  @GetMapping("/user/{userId}/refund-logs")
  public ResponseEntity<ApiResponse<List<RefundLog>>> getUserRefundLogs(@PathVariable Long userId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "User refund logs fetched", 200, refundLogRepository.findByCustomerId(userId)));
  }

  @GetMapping("/user-gateways")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserGateways() {
    List<UserPaymentGateway> all = userPaymentGatewayRepository.findAll();
    List<Map<String, Object>> result = new ArrayList<>();
    for (UserPaymentGateway upg : all) {
      String gwTitle =
          paymentGatewayRepository
              .findById(upg.getPaymentGatewayId())
              .map(PaymentGateway::getTitle)
              .orElse("Gateway #" + upg.getPaymentGatewayId());
      String enabled = upg.getEnabled() != null ? (upg.getEnabled() ? "1" : "0") : "0";
      List<Map<String, Object>> credentialsList = new ArrayList<>();
      List<UserPaymentCredentials> creds =
          userPaymentCredentialsRepository.findByUserPaymentGatewaysId(upg.getId());
      for (UserPaymentCredentials c : creds) {
        credentialsList.add(
            Map.of(
                "id", c.getId(),
                "publicKey", c.getPublicKey() != null ? c.getPublicKey() : "",
                "secretKey", c.getSecretKey() != null ? c.getSecretKey() : "",
                "webhookSecret", c.getWebhookSecret() != null ? c.getWebhookSecret() : ""));
      }
      Map<String, Object> item = new HashMap<>();
      item.put("id", upg.getId());
      item.put("userId", upg.getUserId());
      item.put("paymentGatewayId", upg.getPaymentGatewayId());
      item.put("gatewayTitle", gwTitle);
      item.put("enabled", enabled);
      item.put("credentials", credentialsList);
      result.add(item);
    }
    return ResponseEntity.ok(ApiResponse.success("User gateways fetched", 200, result));
  }

  @PostMapping("/user-gateways/{upgId}/credentials")
  public ResponseEntity<ApiResponse<String>> saveCredentials(
      @PathVariable Long upgId, @RequestBody Map<String, Object> body) {
    try {
      String publicKey = body.get("publicKey") != null ? body.get("publicKey").toString() : "";
      String secretKey = body.get("secretKey") != null ? body.get("secretKey").toString() : "";
      String webhookSecret =
          body.get("webhookSecret") != null ? body.get("webhookSecret").toString() : "";

      UserPaymentCredentials cred =
          UserPaymentCredentials.builder()
              .userPaymentGatewaysId(upgId)
              .publicKey(publicKey)
              .secretKey(secretKey)
              .webhookSecret(webhookSecret)
              .isActive(true)
              .build();
      userPaymentCredentialsRepository.save(cred);
      return ResponseEntity.ok(ApiResponse.success("Credentials saved", 200, "OK"));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to save credentials: " + e.getMessage(), 400));
    }
  }

  @PutMapping("/user-gateways/{upgId}/toggle")
  public ResponseEntity<ApiResponse<String>> toggleGateway(@PathVariable Long upgId) {
    try {
      UserPaymentGateway upg = userPaymentGatewayRepository.findById(upgId).orElse(null);
      if (upg == null) {
        return ResponseEntity.badRequest().body(ApiResponse.error("User gateway not found", 400));
      }
      // If enabling, disable all other enabled gateways for same user
      if (upg.getEnabled() == null || !upg.getEnabled()) {
        List<UserPaymentGateway> others =
            userPaymentGatewayRepository.findByUserIdAndEnabled(upg.getUserId(), true);
        for (UserPaymentGateway other : others) {
          other.setEnabled(false);
          userPaymentGatewayRepository.save(other);
        }
        upg.setEnabled(true);
      } else {
        upg.setEnabled(false);
      }
      userPaymentGatewayRepository.save(upg);
      return ResponseEntity.ok(ApiResponse.success("Gateway toggled", 200, "OK"));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Failed to toggle: " + e.getMessage(), 400));
    }
  }

  @GetMapping("/dashboard")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard(
      @RequestParam(defaultValue = "week") String range) {
    LocalDateTime startDate;
    LocalDateTime now = LocalDateTime.now();

    switch (range) {
      case "today":
        startDate = now.toLocalDate().atStartOfDay();
        break;
      case "month":
        startDate = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        break;
      case "year":
        startDate = now.withDayOfYear(1).toLocalDate().atStartOfDay();
        break;
      case "all":
        startDate = LocalDateTime.of(2020, 1, 1, 0, 0);
        break;
      default: // week
        startDate = now.minusDays(7).toLocalDate().atStartOfDay();
        break;
    }

    List<PaymentLog> logs = paymentLogRepository.findAllByOrderByCreatedAtDesc();
    List<RefundLog> refunds = refundLogRepository.findAll();

    // Filter by date range
    List<PaymentLog> filtered =
        logs.stream()
            .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(startDate))
            .toList();
    List<RefundLog> filteredRefunds =
        refunds.stream()
            .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(startDate))
            .filter(r -> "1".equals(r.getStatus()))
            .toList();

    // Counts
    long succeeded =
        filtered.stream().filter(l -> l.getStatus() == PaymentStatus.SUCCEEDED).count();
    long failed = filtered.stream().filter(l -> l.getStatus() == PaymentStatus.FAILED).count();

    // Payment method breakdown
    Map<String, Long> methodCounts = new HashMap<>();
    for (PaymentLog l : filtered) {
      String method = l.getPaymentMethod() != null ? l.getPaymentMethod() : "Unknown";
      methodCounts.merge(method, 1L, Long::sum);
    }

    // Status breakdown
    Map<String, Long> statusCounts = new HashMap<>();
    for (PaymentLog l : filtered) {
      String status = l.getStatus() != null ? l.getStatus().getLabel() : "Unknown";
      statusCounts.merge(status, 1L, Long::sum);
    }

    // Timeline (daily)
    Map<String, Long> dailyCounts = new HashMap<>();
    Map<String, Double> dailyRevenue = new HashMap<>();
    for (PaymentLog l : filtered) {
      String day = l.getCreatedAt().toLocalDate().toString();
      dailyCounts.merge(day, 1L, Long::sum);
      dailyRevenue.merge(day, l.getAmount() != null ? l.getAmount().doubleValue() : 0, Double::sum);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("totalPayments", filtered.size());
    result.put("succeeded", succeeded);
    result.put("failed", failed);
    result.put("totalRefunds", filteredRefunds.size());
    result.put("methodCounts", methodCounts);
    result.put("statusCounts", statusCounts);
    result.put("dailyCounts", dailyCounts);
    result.put("dailyRevenue", dailyRevenue);

    return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", 200, result));
  }
}
