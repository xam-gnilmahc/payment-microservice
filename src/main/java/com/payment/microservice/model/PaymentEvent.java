package com.payment.microservice.model;

/**
 * PaymentEvent enum tracks which action/event created this payment log entry.
 *
 * Stored as MySQL ENUM('0','1'):
 * '0' = PAYMENT_INTENT → Step 1: PaymentIntent created on Stripe, frontend not yet confirmed
 * '1' = CONFIRM        → Step 2: Frontend confirmed payment after 3DS authentication
 *
 * Each event represents a step in the payment flow:
 * - PAYMENT_INTENT(0): Backend creates PaymentIntent, returns clientSecret to frontend
 * - CONFIRM(1):        Frontend calls stripe.confirmCardPayment(), then backend checks status
 */
public enum PaymentEvent {

    /**
     * Status '0': PaymentIntent created on Stripe via /api/v1/payments.
     * This is Step 1 - backend creates the intent, returns clientSecret to frontend.
     * Status at this point is usually INITIATED (0).
     */
    PAYMENT_INTENT("0", "PAYMENT_INTENT"),

    /**
     * Status '1': Payment confirmed after frontend 3DS authentication.
     * This is Step 2 - frontend calls stripe.confirmCardPayment(clientSecret),
     * handles 3DS if needed, then calls POST /api/v1/payments/confirm.
     * Status at this point is usually PROCESSING (1) or FAILED (2).
     */
    CONFIRM("1", "CONFIRM");

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

    /**
     * Convert integer code from database to PaymentEvent enum.
     * @param code The code ("0" or "1")
     * @return The corresponding PaymentEvent enum value
     * @throws IllegalArgumentException if code is not valid
     */
    public static PaymentEvent fromCode(String code) {
        for (PaymentEvent event : values()) {
            if (event.code.equals(code)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Invalid PaymentEvent code: " + code);
    }
}
