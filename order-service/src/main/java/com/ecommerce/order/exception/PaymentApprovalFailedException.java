package com.ecommerce.order.exception;

/** PG 승인 실패(카드 거절·통신 오류 등) — 주문은 CANCELLED 로 보상된다 (§5.1) */
public class PaymentApprovalFailedException extends RuntimeException {

    public PaymentApprovalFailedException(String message) {
        super(message);
    }
}
