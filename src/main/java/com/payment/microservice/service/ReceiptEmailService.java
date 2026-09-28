package com.payment.microservice.service;

import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
import com.payment.microservice.model.RefundLog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends customer receipt emails from templates in static/email/:
 *
 * <ul>
 *   <li>sendPaymentReceipt — payment success OR failure
 *   <li>sendRefundReceipt — refund success OR failure
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptEmailService {

  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final EmailService emailService;

  @Value("${receipt.email.enabled:true}")
  private boolean enabled;

  /** Sends HTML payment receipt on success or failure (has customer email). */
  public boolean sendPaymentReceipt(PaymentLog p) {
    if (!enabled) {
      log.info(
          "Receipt emails disabled — skip payment receipt id={}", p == null ? null : p.getId());
      return false;
    }
    if (p == null || p.getEmail() == null || p.getEmail().isBlank()) {
      log.warn(
          "Payment receipt skipped: no customer email (log id={})", p == null ? null : p.getId());
      return false;
    }
    if (p.getStatus() != PaymentStatus.SUCCEEDED && p.getStatus() != PaymentStatus.FAILED) {
      return false;
    }
    boolean ok = p.getStatus() == PaymentStatus.SUCCEEDED;
    String subject =
        String.format(
            "%s · %s",
            ok ? "Payment received" : "Payment couldn't be completed",
            formatAmount(p.getCurrency(), p.getAmount()));
    return emailService.sendHtml(p.getEmail(), subject, renderPaymentReceipt(p));
  }

  /** Sends HTML refund receipt on success ("1") or failure ("2") with customer email. */
  public boolean sendRefundReceipt(RefundLog r) {
    if (!enabled) {
      log.info("Receipt emails disabled — skip refund receipt id={}", r == null ? null : r.getId());
      return false;
    }
    if (r == null || r.getEmail() == null || r.getEmail().isBlank()) {
      log.warn(
          "Refund receipt skipped: no customer email (log id={})", r == null ? null : r.getId());
      return false;
    }
    String st = r.getStatus() == null ? "" : r.getStatus();
    if (!"1".equals(st) && !"2".equals(st)) {
      return false;
    }
    boolean ok = "1".equals(st);
    String subject =
        String.format(
            "%s · %s",
            ok ? "Refund on the way" : "Refund couldn't be completed",
            formatAmount(r.getCurrency(), r.getAmount()));
    return emailService.sendHtml(r.getEmail(), subject, renderRefundReceipt(r));
  }

  private String renderPaymentReceipt(PaymentLog p) {
    boolean ok = p.getStatus() == PaymentStatus.SUCCEEDED;
    String template = emailService.loadTemplate("payment-receipt");
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("amountDisplay", formatAmount(p.getCurrency(), p.getAmount()));
    vars.put("date", p.getCreatedAt() != null ? p.getCreatedAt().format(TS) : "-");
    vars.put("method", nullTo(p.getPaymentMethod(), "Card"));
    vars.put("year", String.valueOf(LocalDateTime.now().getYear()));

    String name = firstName(p.getEmail());

    if (ok) {
      vars.put("accent", "#15803d");
      vars.put("eyebrow", "All done");
      vars.put("headline", "Payment successful");
      vars.put("status", "Succeeded");
      vars.put("failureRow", "");
      vars.put(
          "message",
          "Hi" + name + ", your payment is confirmed. Keep this receipt for your records.");
      vars.put("footer", "Questions? We're happy to help.");
    } else {
      vars.put("accent", "#b91c1c");
      vars.put("eyebrow", "Needs attention");
      vars.put("headline", "Payment didn't go through");
      vars.put("status", "Failed");
      String failCode = p.getFailureCode();
      vars.put(
          "failureRow",
          failCode == null || failCode.isBlank() || "null".equals(failCode)
              ? ""
              : failureRow("Failure code", failCode.trim()));
      vars.put(
          "message",
          "Hi"
              + name
              + ", your payment failed and you were not charged. Please try again with another card.");
      vars.put("footer", "Having trouble? Reply and we'll help.");
    }
    return EmailService.fill(template, vars);
  }

  private String renderRefundReceipt(RefundLog r) {
    String st = r.getStatus() == null ? "" : r.getStatus();
    boolean ok = "1".equals(st);
    boolean failed = "2".equals(st);
    String template = emailService.loadTemplate("refund-receipt");
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("amountDisplay", formatAmount(r.getCurrency(), r.getAmount()));
    vars.put("date", r.getCreatedAt() != null ? r.getCreatedAt().format(TS) : "-");
    vars.put("year", String.valueOf(LocalDateTime.now().getYear()));
    vars.put("detail", escape(nullTo(r.getMessage(), "-")));
    String name = firstName(r.getEmail());

    if (ok) {
      vars.put("accent", "#15803d");
      vars.put("eyebrow", "Refund issued");
      vars.put("headline", "Your refund is on the way");
      vars.put("status", "Succeeded");
      vars.put("failureRow", "");
      vars.put(
          "message", "Hi" + name + ", we've started your refund. Most banks show it in 5–10 days.");
      vars.put("footer", "Don't see it after 10 days? Reply here.");
    } else if (failed) {
      vars.put("accent", "#b91c1c");
      vars.put("eyebrow", "Refund failed");
      vars.put("headline", "We couldn't send your refund");
      vars.put("status", "Failed");
      vars.put("failureRow", failureRow("Failure reason", refundFailureReason(r)));
      vars.put(
          "message",
          "Hi"
              + name
              + ", the refund failed and your original payment is unchanged. Contact support and we'll fix it.");
      vars.put("footer", "We'll make sure this is resolved.");
    } else {
      vars.put("accent", "#b45309");
      vars.put("eyebrow", "Refund pending");
      vars.put("headline", "Your refund is processing");
      vars.put("status", "Pending");
      vars.put("failureRow", "");
      vars.put(
          "message", "Hi" + name + ", your refund is processing. It usually settles in 5–10 days.");
      vars.put("footer", "Questions? Just reply to this email.");
    }
    return EmailService.fill(template, vars);
  }

  private static String refundFailureReason(RefundLog r) {
    String msg = r.getMessage();
    if (msg == null || msg.isBlank()) return "Unknown";
    String t = msg.trim();
    if (t.startsWith("Refund failed:")) {
      t = t.substring("Refund failed:".length()).trim();
      int dot = t.indexOf('.');
      if (dot > 0) t = t.substring(0, dot).trim();
    }
    return t.isBlank() ? "Unknown" : t;
  }

  private static String failureRow(String label, String value) {
    if (value == null || value.isBlank() || "Unknown".equals(value)) {
      if (value == null || value.isBlank()) return "";
    }
    return "<tr style=\"background:#ffffff;\">"
        + "<td style=\"padding:14px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;color:#71717a;\">"
        + label
        + "</td>"
        + "<td style=\"padding:14px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;color:#b91c1c;font-weight:700;text-align:right;\">"
        + escape(value == null ? "-" : value)
        + "</td></tr>";
  }

  private static String firstName(String email) {
    if (email == null || email.isBlank()) return "";
    String local = email.split("@")[0];
    if (local.isBlank()) return "";
    String pretty = local.replaceAll("[._-]+", " ").trim();
    if (pretty.isEmpty()) return "";
    return ", " + escape(pretty.substring(0, 1).toUpperCase() + pretty.substring(1));
  }

  private static String formatAmount(String currency, Object amount) {
    String code = upper(currency);
    String symbol =
        switch (code) {
          case "USD" -> "$";
          case "EUR" -> "€";
          case "GBP" -> "£";
          case "INR" -> "₹";
          default -> "";
        };
    if (!symbol.isEmpty()) return symbol + amount;
    if (code.isEmpty()) return String.valueOf(amount);
    return code + " " + amount;
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String upper(String s) {
    return s == null ? "" : s.toUpperCase();
  }

  private static String nullTo(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }
}
