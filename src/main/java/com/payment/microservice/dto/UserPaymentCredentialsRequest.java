package com.payment.microservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPaymentCredentialsRequest {

  @NotNull(message = "User Payment Gateway ID is required")
  @Positive(message = "User Payment Gateway ID must be positive")
  private Long userPaymentGatewaysId;

  private Boolean isActive;

  @Size(max = 500, message = "Public key must be under 500 characters")
  private String publicKey;

  @Size(max = 500, message = "Secret key must be under 500 characters")
  private String secretKey;

  @Size(max = 500, message = "Webhook secret must be under 500 characters")
  private String webhookSecret;

  @Size(max = 10000, message = "Metadata must be under 10000 characters")
  private String metadata;
}
