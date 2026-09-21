package com.payment.microservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "email")
  private String email;

  @Convert(converter = PaymentEventConverter.class)
  @Column(
      nullable = false,
      columnDefinition =
          "ENUM('0','1','2','3','4','5') COMMENT '0=PAYMENT_INTENT, 1=CONFIRM, 2=WEBHOOK_SUCCEEDED, 3=WEBHOOK_FAILED, 4=WEBHOOK_REFUNDED, 5=WEBHOOK_DISPUTED'")
  private PaymentEvent event;

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

  @Convert(converter = PaymentStatusConverter.class)
  @Column(
      nullable = false,
      columnDefinition =
          "ENUM('0','1','2','3') DEFAULT '0' COMMENT '0=INITIATED, 1=PROCESSING, 2=SUCCEEDED, 3=FAILED'")
  private PaymentStatus status;

  @Column(columnDefinition = "TEXT")
  private String message;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "gateway_status")
  private String gatewayStatus;

  @Column(name = "payment_environment")
  private String paymentEnvironment;

  @Column(name = "payment_method")
  private String paymentMethod;
}
