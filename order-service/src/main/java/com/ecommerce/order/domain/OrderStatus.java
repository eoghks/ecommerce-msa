package com.ecommerce.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PAYMENT_PENDING("PAYMENT_PENDING"),          // 결제 승인 대기 — 재고 미차감 (payment-foundation §5)
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    PARTIALLY_CANCELLED("PARTIALLY_CANCELLED"),  // 일부 항목만 취소됨
    CANCELLED("CANCELLED");

    /**
     * H-1: 구매확정 대상 주문 상태 — 재고가 실제로 차감된 주문만 확정할 수 있다.
     * PENDING(미차감)·CANCELLED(전체취소·전체반품)는 확정 대상이 아니다.
     * 전체 반품 승인으로 CANCELLED 가 된 주문도 deliveryStatus 는 DELIVERED 로 남으므로
     * 배송상태만으로 판정하면 취소된 주문이 자동확정된다.
     */
    private static final Set<OrderStatus> CONFIRMABLE_STATUSES =
            EnumSet.of(CONFIRMED, PARTIALLY_CANCELLED);

    private final String code;

    /** 구매확정(수동·자동) 자격이 있는 주문 상태 목록 — 도메인·조회·조건부 UPDATE 공통 기준 */
    public static Collection<OrderStatus> confirmableStatuses() {
        return CONFIRMABLE_STATUSES;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 주문 상태 코드: " + code);
    }
}
