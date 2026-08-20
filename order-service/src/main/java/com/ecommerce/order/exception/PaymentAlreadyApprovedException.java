package com.ecommerce.order.exception;

/** 이미 승인된 주문의 재승인 시도 — 409 (주문당 승인 1건, §8) */
public class PaymentAlreadyApprovedException extends RuntimeException {

    public PaymentAlreadyApprovedException(Long orderId) {
        super("이미 결제가 완료된 주문입니다. orderId=" + orderId);
    }
}
