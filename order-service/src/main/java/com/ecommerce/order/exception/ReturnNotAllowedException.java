package com.ecommerce.order.exception;

/** 반품 자격 미충족(배송완료 아님·취소된 항목·사유 누락) → 400 */
public class ReturnNotAllowedException extends RuntimeException {
    public ReturnNotAllowedException(String message) {
        super(message);
    }
}
