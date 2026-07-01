package com.ecommerce.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    PARTIALLY_CANCELLED("PARTIALLY_CANCELLED"),  // 일부 항목만 취소됨
    CANCELLED("CANCELLED");

    private final String code;

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 주문 상태 코드: " + code);
    }
}
