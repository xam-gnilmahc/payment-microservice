package com.payment.microservice.model;

/**
 * PaymentEvent enum tracks which action/event created this payment log entry.
 *
 * <p>Stored as MySQL ENUM codes: - '0' = PAYMENT_INTENT: Backend created PaymentIntent - '1' =
 * CONFIRM: Frontend confirmed payment - '2' = WEBHOOK_SUCCEEDED: Stripe webhook - payment succeeded
 * - '3' = WEBHOOK_FAILED: Stripe webhook - payment failed - '4' = WEBHOOK_REFUNDED: Stripe webhook
 * - charge refunded - '5' = WEBHOOK_DISPUTED: Stripe webhook - charge disputed
 */
public enum PaymentEvent {
  PAYMENT_INTENT("0", "PAYMENT_INTENT"),
  CONFIRM("1", "CONFIRM"),
  WEBHOOK_SUCCEEDED("2", "WEBHOOK_SUCCEEDED"),
  WEBHOOK_FAILED("3", "WEBHOOK_FAILED"),
  WEBHOOK_REFUNDED("4", "WEBHOOK_REFUNDED"),
  WEBHOOK_DISPUTED("5", "WEBHOOK_DISPUTED");

  private final String code;
  private final String label;

  PaymentEvent(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public static PaymentEvent fromCode(String code) {
    for (PaymentEvent event : values()) {
      if (event.code.equals(code)) {
        return event;
      }
    }
    throw new IllegalArgumentException("Invalid PaymentEvent code: " + code);
  }
}
