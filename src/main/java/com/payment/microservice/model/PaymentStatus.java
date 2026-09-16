package com.payment.microservice.model;

/**
 * PaymentStatus enum defines all possible states of a payment in the system.
 *
 * Each status has an integer code (0-2) and a string label.
 * Use the integer code in the database for easy filtering and querying.
 *
 * Payment Flow:
 *   INITIATED(0) → PROCESSING(1) → FAILED(2)
 *                     (or success stays at PROCESSING)
 */
public enum PaymentStatus {

    /**
     * Status 0: PaymentIntent created on Stripe via /api/v1/payments
     * Card is NOT charged yet. Waiting for frontend to confirm.
     */
    INITIATED(0, "INITIATED"),

    /**
     * Status 1: Payment is being processed.
     * This covers all intermediate states:
     * - Frontend is confirming payment
     * - 3DS authentication in progress
     * - Payment succeeded (success is also PROCESSING)
     */
    PROCESSING(1, "PROCESSING"),

    /**
     * Status 2: Payment failed due to card declined, insufficient funds,
     * 3DS timeout, or any other error from Stripe.
     * Error message is stored in the message column.
     */
    FAILED(2, "FAILED");

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

    /**
     * Convert integer code from database to PaymentStatus enum.
     * @param code The integer status code (0, 1, 2)
     * @return The corresponding PaymentStatus enum value
     * @throws IllegalArgumentException if code is not a valid status
     */
    public static PaymentStatus fromCode(int code) {
        for (PaymentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid PaymentStatus code: " + code);
    }

    /**
     * Convert status label string to PaymentStatus enum.
     * @param label The status label (e.g., "INITIATED", "PROCESSING", "FAILED")
     * @return The corresponding PaymentStatus enum value
     * @throws IllegalArgumentException if label is not a valid status
     */
    public static PaymentStatus fromLabel(String label) {
        for (PaymentStatus status : values()) {
            if (status.label.equalsIgnoreCase(label)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid PaymentStatus label: " + label);
    }
}
