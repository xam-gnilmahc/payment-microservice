package com.payment.microservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_payment_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPaymentCredentials {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "is_active")
  private Boolean isActive;

  @Column(name = "user_payment_gateways_id", nullable = false)
  private Long userPaymentGatewaysId;

  @Column(name = "public_key", length = 500)
  private String publicKey;

  @Column(name = "secret_key", length = 500)
  private String secretKey;

  @Column(name = "webhook_secret", length = 500)
  private String webhookSecret;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
