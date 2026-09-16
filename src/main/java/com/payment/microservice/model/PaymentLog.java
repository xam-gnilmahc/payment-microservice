package com.payment.microservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * Payment event - tracks which step of the payment flow this log entry is:
     * '0' = PAYMENT_INTENT → Step 1: PaymentIntent created, waiting for frontend confirmation
     * '1' = CONFIRM        → Step 2: Frontend confirmed, payment processed
     */
    @Convert(converter = PaymentEventConverter.class)
    @Column(nullable = false, columnDefinition = "ENUM('0','1') COMMENT '0=PAYMENT_INTENT, 1=CONFIRM'")
    private PaymentEvent event;

    /**
     * Payment status stored as MySQL ENUM:
     * '0' = INITIATED   → PaymentIntent created on Stripe, waiting for frontend to confirm
     * '1' = PROCESSING  → Payment is in progress (confirming, 3DS auth, or succeeded)
     * '2' = FAILED      → Payment failed (card declined, insufficient funds, error from Stripe)
     *
     * Default is '0' (INITIATED) when a new payment log is created.
     */
    @Convert(converter = PaymentStatusConverter.class)
    @Column(nullable = false, columnDefinition = "ENUM('0','1','2') DEFAULT '0' COMMENT '0=INITIATED, 1=PROCESSING, 2=FAILED'")
    private PaymentStatus status;

    @Column(nullable = false)
    private String gateway;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "charge_id")
    private String chargeId;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /**
     * Stores messages for all payment stages:
     * - INITIATED:   "PaymentIntent created successfully" or error message
     * - PROCESSING:  "Frontend confirming payment" / "Payment successful" / "3DS pending"
     * - FAILED:      The error/failure reason from Stripe
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
