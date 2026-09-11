package com.payment.microservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserCredentialsRequest {

    @NotNull(message = "User ID is required")
    private Integer userId;

    @NotNull(message = "Gateway is required")
    private Integer gateway;

    @NotBlank(message = "Gateway name is required")
    private String gatewayName;

    @NotBlank(message = "Public key is required")
    private String publicKey;

    @NotBlank(message = "Secret key is required")
    private String secretKey;

    private String webhookSecret;

    private String metadata;
}
