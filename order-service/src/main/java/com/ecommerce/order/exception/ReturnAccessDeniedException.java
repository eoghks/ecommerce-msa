package com.ecommerce.order.exception;

/** 반품을 처리·조회할 권한이 없는 사용자(본인 상품이 없는 SELLER, 일반 USER 등) → 403 */
public class ReturnAccessDeniedException extends RuntimeException {
    public ReturnAccessDeniedException(String message) {
        super(message);
    }
}
