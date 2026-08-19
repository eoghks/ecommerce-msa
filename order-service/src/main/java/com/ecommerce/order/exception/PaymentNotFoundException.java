package com.ecommerce.order.exception;

/** 결제 정보 없음 / 타인 결제 접근 — 404 로 응답해 존재 여부를 노출하지 않는다 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long orderId) {
        super("결제 정보를 찾을 수 없습니다. orderId=" + orderId);
    }
}
