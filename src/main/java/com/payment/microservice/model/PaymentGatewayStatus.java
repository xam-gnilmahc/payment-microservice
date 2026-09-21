package com.payment.microservice.model;

import lombok.Getter;

@Getter
public enum PaymentGatewayStatus {
  INACTIVE("0"),
  ACTIVE("1");

  private final String code;

  PaymentGatewayStatus(String code) {
    this.code = code;
  }

  public static PaymentGatewayStatus fromCode(String code) {
    for (PaymentGatewayStatus status : values()) {
      if (status.code.equals(code)) return status;
    }
    throw new IllegalArgumentException("Unknown code: " + code);
  }
}
