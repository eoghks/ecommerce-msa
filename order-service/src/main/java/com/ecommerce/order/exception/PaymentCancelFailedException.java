package com.ecommerce.order.exception;

/**
 * PG 취소·환불 실패 — 호출한 트랜잭션을 롤백시켜 "환불 안 됐는데 환불 완료" 상태를 막는다 (§5.1).
 */
public class PaymentCancelFailedException extends RuntimeException {

    public PaymentCancelFailedException(String message) {
        super(message);
    }
}
