package com.payment.microservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 100, message = "Title must be 2-100 characters")
    private String title;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;

    @Size(max = 255, message = "Image path must be under 255 characters")
    private String image;

    private String status;
}
