package com.payment.microservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "refund_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transaction_id", nullable = false)
  private String transactionId;

  @Column(name = "charge_id", nullable = false)
  private String chargeId;

  @Column(name = "refund_id", nullable = false)
  private String refundId;

  @Column(name = "card_reference")
  private String cardReference;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  @Column(columnDefinition = "VARCHAR(10) DEFAULT '0'")
  private String status;

  private String message;

  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (status == null) status = "0";
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
