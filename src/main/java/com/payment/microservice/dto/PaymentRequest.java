package com.payment.microservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Gateway ID is required")
    private Long gatewayId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String token;

    private String email;

    private String name;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String zipCode;

    private String country;

    private String paymentMethod;
}
