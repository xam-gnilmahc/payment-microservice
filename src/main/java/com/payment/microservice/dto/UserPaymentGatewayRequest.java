package com.payment.microservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPaymentGatewayRequest {

  @NotNull(message = "User ID is required")
  @Positive(message = "User ID must be positive")
  private Long userId;

  @NotNull(message = "Payment Gateway ID is required")
  @Positive(message = "Payment Gateway ID must be positive")
  private Long paymentGatewayId;

  private Boolean enabled;
}
