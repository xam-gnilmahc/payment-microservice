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
@Table(name = "payment_gateways")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGateway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image")
    private String image;

    @Convert(converter = PaymentGatewayStatusConverter.class)
    @Column(nullable = false, columnDefinition = "ENUM('0','1') DEFAULT '1' COMMENT '0=INACTIVE, 1=ACTIVE'")
    private PaymentGatewayStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
