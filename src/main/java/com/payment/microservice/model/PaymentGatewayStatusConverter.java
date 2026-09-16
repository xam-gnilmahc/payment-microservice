package com.payment.microservice.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentGatewayStatusConverter implements AttributeConverter<PaymentGatewayStatus, String> {
    @Override
    public String convertToDatabaseColumn(PaymentGatewayStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }
    @Override
    public PaymentGatewayStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentGatewayStatus.fromCode(dbData);
    }
}
