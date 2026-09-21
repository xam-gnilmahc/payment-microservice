package com.payment.microservice.model;

public enum RefundStatus {
  PENDING("0"),
  SUCCESS("1"),
  FAILED("2");

  private final String code;

  RefundStatus(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static RefundStatus fromCode(String code) {
    for (RefundStatus s : values()) {
      if (s.code.equals(code)) return s;
    }
    return PENDING;
  }
}
