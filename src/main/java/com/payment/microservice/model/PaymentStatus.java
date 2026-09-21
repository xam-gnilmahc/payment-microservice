package com.payment.microservice.model;

/**
 * PaymentStatus enum defines all possible states of a payment in the system.
 *
 * <p>Each status has an integer code (0-3) and a string label. Use the integer code in the database
 * for easy filtering and querying.
 *
 * <p>Payment Flow: INITIATED(0) → PROCESSING(1) → SUCCEEDED(2) or FAILED(3)
 */
public enum PaymentStatus {

  /**
   * Status 0: PaymentIntent created via API. Card is NOT charged yet. Waiting for frontend to
   * confirm.
   */
  INITIATED(0, "INITIATED"),

  /** Status 1: Payment is being processed — confirming, 3DS auth, or in progress. */
  PROCESSING(1, "PROCESSING"),

  /** Status 2: Payment completed successfully. Charge created, receipt available. */
  SUCCEEDED(2, "SUCCEEDED"),

  /** Status 3: Payment failed — card declined, insufficient funds, error from gateway. */
  FAILED(3, "FAILED");

  private final int code;
  private final String label;

  PaymentStatus(int code, String label) {
    this.code = code;
    this.label = label;
  }

  public int getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public static PaymentStatus fromCode(int code) {
    for (PaymentStatus status : values()) {
      if (status.code == code) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid PaymentStatus code: " + code);
  }

  public static PaymentStatus fromLabel(String label) {
    for (PaymentStatus status : values()) {
      if (status.label.equalsIgnoreCase(label)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid PaymentStatus label: " + label);
  }
}
