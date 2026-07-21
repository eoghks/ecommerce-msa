package com.ecommerce.order.domain;

/**
 * 배송 진행 상태. PREPARING(준비중) → SHIPPING(배송중) → DELIVERED(배송완료).
 * 전진만 허용(되돌리기 불가). OrderStatus(주문 라이프사이클)와는 별도 축.
 */
public enum DeliveryStatus {

    PREPARING,   // 준비중
    SHIPPING,    // 배송중
    DELIVERED;   // 배송완료

    /**
     * 현재 상태에서 바로 다음 단계로의 전이인지 판정.
     * 역행·건너뜀·동일 상태 재설정은 모두 false.
     */
    public boolean canAdvanceTo(DeliveryStatus next) {
        return next != null && next.ordinal() == this.ordinal() + 1;
    }
}
