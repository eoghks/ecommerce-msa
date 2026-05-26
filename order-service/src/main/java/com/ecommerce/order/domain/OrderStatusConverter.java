package com.ecommerce.order.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * OrderStatus ↔ DB VARCHAR 변환.
 * enum 클래스명이 바뀌어도 DB 값(code)은 영향 없음.
 */
@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus status) {
        if (status == null) return null;
        return status.getCode();
    }

    @Override
    public OrderStatus convertToEntityAttribute(String code) {
        if (code == null) return null;
        return OrderStatus.fromCode(code);
    }
}
