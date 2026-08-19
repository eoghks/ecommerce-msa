package com.ecommerce.order.exception;

/** 이미 구매확정된 주문에 재확정 요청 → 409 Conflict (반품 상태충돌 409와 일관) */
public class PurchaseAlreadyConfirmedException extends RuntimeException {
    public PurchaseAlreadyConfirmedException(Long orderId) {
        super("이미 구매확정된 주문입니다. orderId=" + orderId);
    }
}
