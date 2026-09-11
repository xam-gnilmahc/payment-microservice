package com.payment.microservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "gateway", nullable = false)
    private Integer gateway;

    @Column(name = "gateway_name", nullable = false)
    private String gatewayName;

    @Column(name = "public_key", nullable = false, length = 500)
    private String publicKey;

    @Column(name = "secret_key", nullable = false, length = 500)
    private String secretKey;

    @Column(name = "webhook_secret", length = 500)
    private String webhookSecret;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
