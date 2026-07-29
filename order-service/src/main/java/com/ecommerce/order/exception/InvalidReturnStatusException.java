package com.ecommerce.order.exception;

/** 잘못된 반품 상태 전이(REQUESTED 외 승인/거부, APPROVED 외 환불) → 400 */
public class InvalidReturnStatusException extends RuntimeException {
    public InvalidReturnStatusException(String message) {
        super(message);
    }
}
