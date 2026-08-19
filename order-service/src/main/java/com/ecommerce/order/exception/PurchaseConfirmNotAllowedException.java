package com.ecommerce.order.exception;

/** 구매확정 자격 미충족(배송완료 전) → 400 */
public class PurchaseConfirmNotAllowedException extends RuntimeException {
    public PurchaseConfirmNotAllowedException(String message) {
        super(message);
    }
}
