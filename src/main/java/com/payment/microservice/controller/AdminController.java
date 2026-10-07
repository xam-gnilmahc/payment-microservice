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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import com.payment.microservice.traits.CurrentUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

  private final UserRepository userRepository;
  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
  private final PaymentGatewayRepository paymentGatewayRepository;
  private final UserPaymentGatewayRepository userPaymentGatewayRepository;
  private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;
  private final RefundLogRepository refundLogRepository;
  private final PaymentLogRepository paymentLogRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecurityContextRepository securityContextRepository;

  @GetMapping("/users")
  public ResponseEntity<ApiResponse<Iterable<User>>> getUsers() {
    return ResponseEntity.ok(ApiResponse.success("Users fetched", 200, userRepository.findAll()));
  }

  // Sign in as a user.
  //
  // The admin's own password is not enough on its own: the target account's password is required
  // too, so a hijacked admin session cannot silently take over an account. On success the session
  // is REPLACED, which means the admin is signed out of the panel and lands as that user.
  @PostMapping("/users/{id}/login-as")
  public ResponseEntity<ApiResponse<Object>> loginAsUser(
      @PathVariable Long id,
      @RequestBody Map<String, Object> body,
      jakarta.servlet.http.HttpServletRequest httpRequest,
      jakarta.servlet.http.HttpServletResponse httpResponse) {
    User target = userRepository.findById(id).orElse(null);
    if (target == null) {
      return ResponseEntity.badRequest().body(ApiResponse.error("User not found", 404));
    }
    if (Boolean.FALSE.equals(target.getIsActive())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(ApiResponse.error("That account is blocked. Unblock it first.", 403));
    }
    String password = body.get("password") != null ? body.get("password").toString() : "";
    if (!passwordEncoder.matches(password, target.getPassword())) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("That password does not match this account", 400));
    }

    // replace the session: from here this browser is that user, not the admin
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(target, null, target.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    securityContextRepository.saveContext(SecurityContextHolder.getContext(), httpRequest, httpResponse);

    return ResponseEntity.ok(
        ApiResponse.success(
            "Signed in as " + target.getEmail() + ". You are now signed in as that user.",
            200,
            Map.of(
                "userId", target.getId(),
                "email", target.getEmail(),
                "name", target.getName() != null ? target.getName() : "",
                "isSuperAdmin", target.getIsSuperAdmin() != null ? target.getIsSuperAdmin() : "0")));
  }

  // Block / unblock an account. Blocking flips is_active, which is what login checks, and also ends
  // any session the user already has, so a blocked user is signed out immediately instead of
  // staying signed in until the session times out.
  @PutMapping("/users/{id}/status")
  public ResponseEntity<ApiResponse<Object>> toggleUserStatus(@PathVariable Long id) {
    User user = userRepository.findById(id).orElse(null);
    if (user == null) {
      return ResponseEntity.badRequest().body(ApiResponse.error("User not found", 404));
    }
    if (user.getId().equals(CurrentUser.id())) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("You cannot block your own account", 400));
    }
    boolean nowActive = Boolean.FALSE.equals(user.getIsActive());
    user.setIsActive(nowActive);
    userRepository.save(user);

    if (!nowActive) {
      int ended = endSessionsOf(user);
      log.info("Blocked user id={} and ended {} active session(s)", id, ended);
    }

    return ResponseEntity.ok(
        ApiResponse.success(
            nowActive ? "User unblocked" : "User blocked",
            200,
            Map.of("userId", user.getId(), "isActive", nowActive)));
  }

  /**
   * Deletes this user's rows from SPRING_SESSION, which ends every session they are signed in on.
   * Their next request finds no session and comes back as 401, so the browser lands on the login
   * page straight away instead of staying signed in until the timeout expires.
   *
   * @return how many sessions were ended
   */
  private int endSessionsOf(User user) {
    var sessions =
        sessionRepository.findByIndexNameAndIndexValue(
            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getEmail());
    int ended = 0;
    for (Session session : sessions.values()) {
      sessionRepository.deleteById(session.getId());
      ended++;
    }
    return ended;
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
  
  // Admin can view all payment logs, optionally filtered by status, with pagination.
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
  
  // Admin can view all refund logs with pagination.
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
  
  // Admin can view payment logs for a specific user, optionally filtered by status, with pagination.
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
  
  // Admin can view refund logs for a specific user with pagination.
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
  
  // Admin can view all user gateways with their credentials and enabled status.
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
  
  // Admin can save credentials for a user gateway. The request body should contain publicKey, secretKey, and webhookSecret.
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
  
  // Admin can toggle a user gateway's enabled status. If enabling, it disables all other gateways for the same user.
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
  
  // Admin can view a dashboard of payment and refund statistics over a specified date range, optionally filtered by customer ID. The range can be "today", "week", "month", "year", "all", or "custom" with startDate and endDate parameters. The grain parameter can be used to specify the granularity of the data (e.g., "auto", "day", "week", "month").
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
            ? paymentLogRepository.countGroupByPaymentMethodAndCustomer(
                startDateDt, endDateDt, customerId)
            : paymentLogRepository.countGroupByPaymentMethod(startDateDt, endDateDt);
    for (Object[] row : methodRows) {
      methodCounts.put((String) row[0], (Long) row[1]);
    }

    Map<String, Long> statusCounts = new HashMap<>();
    List<Object[]> statusRows =
        byCustomer
            ? paymentLogRepository.countGroupByStatusAndCustomer(startDateDt, endDateDt, customerId)
            : paymentLogRepository.countGroupByStatus(startDateDt, endDateDt);
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

    // The dashboard only ships the newest 100 rows, but the panel needs to say so, otherwise
    // "100 logs" reads as if that is the whole range. So the real count goes out too.
    long logsTotal =
        byCustomer
            ? paymentLogRepository.countBetweenAndCustomer(startDateDt, endDateDt, customerId)
            : paymentLogRepository.countBetween(startDateDt, endDateDt);
    List<PaymentLog> logRows =
        byCustomer
            ? paymentLogRepository.findBetweenAndCustomer(startDateDt, endDateDt, customerId)
            : paymentLogRepository.findBetween(startDateDt, endDateDt);
    if (logRows.size() > 100) {
      logRows = new ArrayList<>(logRows.subList(0, 100));
    }
    result.put("paymentLogs", logRows);
    result.put("paymentLogsTotal", logsTotal);

    return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", 200, result));
  }
  
  // Determine the grain (day, week, month, year) based on the range and start/end dates. If grain is specified and valid, use it; otherwise, infer from the date range.
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
