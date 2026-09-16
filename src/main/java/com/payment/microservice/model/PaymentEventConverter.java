package com.payment.microservice.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentEventConverter implements AttributeConverter<PaymentEvent, String> {

    @Override
    public String convertToDatabaseColumn(PaymentEvent event) {
        if (event == null) return null;
        return event.getCode();
    }

    @Override
    public PaymentEvent convertToEntityAttribute(String code) {
        if (code == null) return null;
        return PaymentEvent.fromCode(code);
    }
}
