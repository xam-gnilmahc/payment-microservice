package com.payment.microservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

  @NotBlank(message = "Charge ID is required")
  private String chargeId;

  /** Refund reason: duplicate, fraudulent, or requested_by_customer */
  private String reason;

  private Long customerId;
}
