package com.ecommerce.order.exception;

/** 동일 항목에 진행 중(REQUESTED/APPROVED/REFUNDED)인 반품이 이미 있음 → 409 */
public class DuplicateReturnRequestException extends RuntimeException {
    public DuplicateReturnRequestException(Long orderItemId) {
        super("이미 진행 중인 반품이 있습니다. itemId=" + orderItemId);
    }
}
