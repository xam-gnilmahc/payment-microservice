package com.payment.microservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

  // Filled in by the server from the session, never sent by the browser, so it is not validated.
  private Long customerId;

  @NotNull(message = "Amount is required")
  @Positive(message = "Amount must be positive")
  @DecimalMin(value = "0.50", message = "Amount must be at least 0.50")
  @DecimalMax(value = "999999.99", message = "Amount must not exceed 999999.99")
  private BigDecimal amount;

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
