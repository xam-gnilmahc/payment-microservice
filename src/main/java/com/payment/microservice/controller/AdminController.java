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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @GetMapping("/payment-logs")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentLogs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String status) {
    org.springframework.data.domain.PageRequest pageable =
        org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<PaymentLog> logPage;
    if (status != null && !status.isBlank()) {
      PaymentStatus st;
      try {
        st = PaymentStatus.valueOf(status.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Invalid status: " + status, 400));
      }
      logPage = paymentLogRepository.findByStatusOrderByIdDesc(st, pageable);
    } else {
      logPage = paymentLogRepository.findAllByIdDesc(pageable);
    }
    Map<String, Object> response = new HashMap<>();
    response.put("logs", logPage.getContent());
    response.put("currentPage", logPage.getNumber());
    response.put("totalPages", logPage.getTotalPages());
    response.put("totalElements", logPage.getTotalElements());
    return ResponseEntity.ok(ApiResponse.success("Payment logs fetched", 200, response));
  }

  @GetMapping("/refund-logs")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getRefundLogs(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    org.springframework.data.domain.Page<RefundLog> logPage =
        refundLogRepository.findAllByIdDesc(
            org.springframework.data.domain.PageRequest.of(page, size));
    Map<String, Object> response = new HashMap<>();
    response.put("logs", logPage.getContent());
    response.put("currentPage", logPage.getNumber());
    response.put("totalPages", logPage.getTotalPages());
    response.put("totalElements", logPage.getTotalElements());
    return ResponseEntity.ok(ApiResponse.success("Refund logs fetched", 200, response));
  }

  @GetMapping("/user/{userId}/payment-logs")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getUserPaymentLogs(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String status) {
    org.springframework.data.domain.PageRequest pageable =
        org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<PaymentLog> logPage;
    if (status != null && !status.isBlank()) {
      PaymentStatus st;
      try {
        st = PaymentStatus.valueOf(status.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Invalid status: " + status, 400));
      }
      logPage = paymentLogRepository.findByCustomerIdAndStatusOrderByIdDesc(userId, st, pageable);
    } else {
      logPage = paymentLogRepository.findByCustomerIdOrderByIdDesc(userId, pageable);
    }
    Map<String, Object> response = new HashMap<>();
    response.put("logs", logPage.getContent());
    response.put("currentPage", logPage.getNumber());
    response.put("totalPages", logPage.getTotalPages());
    response.put("totalElements", logPage.getTotalElements());
    return ResponseEntity.ok(ApiResponse.success("User payment logs fetched", 200, response));
  }

  @GetMapping("/user/{userId}/refund-logs")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getUserRefundLogs(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    org.springframework.data.domain.Page<RefundLog> logPage =
        refundLogRepository.findByCustomerIdOrderByIdDesc(
            userId, org.springframework.data.domain.PageRequest.of(page, size));
    Map<String, Object> response = new HashMap<>();
    response.put("logs", logPage.getContent());
    response.put("currentPage", logPage.getNumber());
    response.put("totalPages", logPage.getTotalPages());
    response.put("totalElements", logPage.getTotalElements());
    return ResponseEntity.ok(ApiResponse.success("User refund logs fetched", 200, response));
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
      @RequestParam(defaultValue = "week") String range,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) Long customerId,
      @RequestParam(defaultValue = "auto") String grain) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startDt;
    LocalDateTime endDt = now.toLocalDate().plusDays(1).atStartOfDay();

    if (("custom".equals(range) || (startDate != null && endDate != null))
        && startDate != null
        && endDate != null
        && !startDate.isBlank()
        && !endDate.isBlank()) {
      try {
        startDt = java.time.LocalDate.parse(startDate.trim()).atStartOfDay();
        endDt = java.time.LocalDate.parse(endDate.trim()).plusDays(1).atStartOfDay();
        if (endDt.isBefore(startDt)) {
          LocalDateTime tmp = startDt;
          startDt = endDt.minusDays(1);
          endDt = tmp.plusDays(1);
        }
      } catch (DateTimeParseException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Invalid startDate/endDate", 400));
      }
    } else {
      switch (range) {
        case "today":
          startDt = now.toLocalDate().atStartOfDay();
          endDt = startDt.plusDays(1);
          break;
        case "month":
          startDt = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
          break;
        case "year":
          startDt = now.withDayOfYear(1).toLocalDate().atStartOfDay();
          break;
        case "all":
          startDt = LocalDateTime.of(2020, 1, 1, 0, 0);
          break;
        default: // week = last 7 days including today
          startDt = now.minusDays(6).toLocalDate().atStartOfDay();
          break;
      }
    }

    LocalDateTime startDateDt = startDt;
    LocalDateTime endDateDt = endDt;

    // Previous window of equal length for comparison
    long rangeDays = java.time.Duration.between(startDateDt, endDateDt).toDays();
    if (rangeDays < 1) rangeDays = 1;
    LocalDateTime prevStart = startDateDt.minusDays(rangeDays);
    LocalDateTime prevEnd = startDateDt;

    boolean byCustomer = customerId != null;

    long totalPayments =
        byCustomer
            ? paymentLogRepository.countBetweenAndCustomer(startDateDt, endDateDt, customerId)
            : paymentLogRepository.countBetween(startDateDt, endDateDt);
    long succeeded =
        byCustomer
            ? paymentLogRepository.countBetweenAndStatusAndCustomer(
                startDateDt, endDateDt, PaymentStatus.SUCCEEDED, customerId)
            : paymentLogRepository.countBetweenAndStatus(
                startDateDt, endDateDt, PaymentStatus.SUCCEEDED);
    long failed =
        byCustomer
            ? paymentLogRepository.countBetweenAndStatusAndCustomer(
                startDateDt, endDateDt, PaymentStatus.FAILED, customerId)
            : paymentLogRepository.countBetweenAndStatus(
                startDateDt, endDateDt, PaymentStatus.FAILED);
    long totalRefunds =
        byCustomer
            ? refundLogRepository.countSucceededBetweenAndCustomer(
                startDateDt, endDateDt, customerId)
            : refundLogRepository.countSucceededBetween(startDateDt, endDateDt);
    double refundedAmount =
        byCustomer
            ? refundLogRepository.sumSucceededAmountBetweenAndCustomer(
                startDateDt, endDateDt, customerId)
            : refundLogRepository.sumSucceededAmountBetween(startDateDt, endDateDt);

    long prevTotal =
        byCustomer
            ? paymentLogRepository.countBetweenAndCustomer(prevStart, prevEnd, customerId)
            : paymentLogRepository.countBetween(prevStart, prevEnd);
    long prevSucceeded =
        byCustomer
            ? paymentLogRepository.countBetweenAndStatusAndCustomer(
                prevStart, prevEnd, PaymentStatus.SUCCEEDED, customerId)
            : paymentLogRepository.countBetweenAndStatus(
                prevStart, prevEnd, PaymentStatus.SUCCEEDED);
    long prevFailed =
        byCustomer
            ? paymentLogRepository.countBetweenAndStatusAndCustomer(
                prevStart, prevEnd, PaymentStatus.FAILED, customerId)
            : paymentLogRepository.countBetweenAndStatus(prevStart, prevEnd, PaymentStatus.FAILED);
    long prevRefunds =
        byCustomer
            ? refundLogRepository.countSucceededBetweenAndCustomer(prevStart, prevEnd, customerId)
            : refundLogRepository.countSucceededBetween(prevStart, prevEnd);
    double prevRefunded =
        byCustomer
            ? refundLogRepository.sumSucceededAmountBetweenAndCustomer(
                prevStart, prevEnd, customerId)
            : refundLogRepository.sumSucceededAmountBetween(prevStart, prevEnd);

    Map<String, Long> methodCounts = new HashMap<>();
    List<Object[]> methodRows =
        byCustomer
            ? paymentLogRepository.countGroupByPaymentMethodAndCustomer(startDateDt, customerId)
            : paymentLogRepository.countGroupByPaymentMethod(startDateDt);
    for (Object[] row : methodRows) {
      methodCounts.put((String) row[0], (Long) row[1]);
    }

    Map<String, Long> statusCounts = new HashMap<>();
    List<Object[]> statusRows =
        byCustomer
            ? paymentLogRepository.countGroupByStatusAndCustomer(startDateDt, customerId)
            : paymentLogRepository.countGroupByStatus(startDateDt);
    for (Object[] row : statusRows) {
      PaymentStatus st = (PaymentStatus) row[0];
      statusCounts.put(st != null ? st.getLabel() : "Unknown", (Long) row[1]);
    }

    String effectiveGrain = resolveGrain(grain, range, startDateDt, endDateDt);
    List<Object[]> seriesRows;
    switch (effectiveGrain) {
      case "day" ->
          seriesRows =
              byCustomer
                  ? paymentLogRepository.dailyBreakdownAndCustomer(
                      startDateDt, endDateDt, customerId)
                  : paymentLogRepository.dailyBreakdown(startDateDt, endDateDt);
      case "week" ->
          seriesRows =
              byCustomer
                  ? paymentLogRepository.weeklyBreakdownAndCustomer(
                      startDateDt, endDateDt, customerId)
                  : paymentLogRepository.weeklyBreakdown(startDateDt, endDateDt);
      case "year" ->
          seriesRows =
              byCustomer
                  ? paymentLogRepository.yearlyBreakdownAndCustomer(
                      startDateDt, endDateDt, customerId)
                  : paymentLogRepository.yearlyBreakdown(startDateDt, endDateDt);
      default -> // month
          seriesRows =
              byCustomer
                  ? paymentLogRepository.monthlyBreakdownAndCustomer(
                      startDateDt, endDateDt, customerId)
                  : paymentLogRepository.monthlyBreakdown(startDateDt, endDateDt);
    }

    List<Map<String, Object>> series = new ArrayList<>();
    Map<String, Long> dailyCounts = new HashMap<>();
    Map<String, Double> dailyRevenue = new HashMap<>();
    double revenue = 0;
    for (Object[] row : seriesRows) {
      String key = row[0] != null ? row[0].toString() : "unknown";
      long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
      double rev = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
      long succ = row[3] != null ? ((Number) row[3]).longValue() : 0L;
      long fail = row[4] != null ? ((Number) row[4]).longValue() : 0L;
      revenue += rev;
      dailyCounts.put(key, count);
      dailyRevenue.put(key, rev);
      Map<String, Object> point = new HashMap<>();
      point.put("period", key);
      point.put("payments", count);
      point.put("revenue", rev);
      point.put("succeeded", succ);
      point.put("failed", fail);
      point.put("successRate", count > 0 ? Math.round(succ * 1000.0 / count) / 10.0 : 0.0);
      series.add(point);
    }

    Map<String, Object> previous = new HashMap<>();
    previous.put("totalPayments", prevTotal);
    previous.put("succeeded", prevSucceeded);
    previous.put("failed", prevFailed);
    previous.put("totalRefunds", prevRefunds);
    previous.put("refundedAmount", prevRefunded);
    double prevRevenue = 0;
    List<Object[]> prevSeries =
        byCustomer
            ? paymentLogRepository.dailyBreakdownAndCustomer(prevStart, prevEnd, customerId)
            : paymentLogRepository.dailyBreakdown(prevStart, prevEnd);
    for (Object[] row : prevSeries) {
      if (row[2] != null) prevRevenue += ((Number) row[2]).doubleValue();
    }
    previous.put("revenue", prevRevenue);
    previous.put("start", prevStart.toLocalDate().toString());
    previous.put("end", prevEnd.toLocalDate().toString());

    Map<String, Object> result = new HashMap<>();
    result.put("totalPayments", totalPayments);
    result.put("succeeded", succeeded);
    result.put("failed", failed);
    result.put("totalRefunds", totalRefunds);
    result.put("refundedAmount", refundedAmount);
    result.put("revenue", revenue);
    result.put("customerId", customerId);
    result.put("methodCounts", methodCounts);
    result.put("statusCounts", statusCounts);
    result.put("dailyCounts", dailyCounts);
    result.put("dailyRevenue", dailyRevenue);
    result.put("series", series);
    result.put("grain", effectiveGrain);
    result.put("previous", previous);
    result.put("rangeStart", startDateDt.toLocalDate().toString());
    result.put("rangeEnd", endDateDt.minusDays(1).toLocalDate().toString());
    result.put("range", range);

    List<PaymentLog> logRows =
        byCustomer
            ? paymentLogRepository.findBetweenAndCustomer(startDateDt, endDateDt, customerId)
            : paymentLogRepository.findBetween(startDateDt, endDateDt);
    if (logRows.size() > 100) {
      logRows = new ArrayList<>(logRows.subList(0, 100));
    }
    result.put("paymentLogs", logRows);

    return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", 200, result));
  }

  private static String resolveGrain(
      String grain, String range, LocalDateTime startDate, LocalDateTime endDate) {
    if (grain != null && !grain.isBlank() && !"auto".equals(grain)) {
      if (Set.of("day", "week", "month", "year").contains(grain)) return grain;
    }
    long days = java.time.Duration.between(startDate, endDate).toDays();
    if (days <= 14) return "day";
    if (days <= 90) return "week";
    if (days <= 400) return "month";
    return "month";
  }
}
