package com.payment.microservice.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

  @Override
  public String convertToDatabaseColumn(PaymentStatus status) {
    if (status == null) return null;
    return String.valueOf(status.getCode());
  }

  @Override
  public PaymentStatus convertToEntityAttribute(String code) {
    if (code == null) return null;
    return PaymentStatus.fromCode(Integer.parseInt(code));
  }
}
