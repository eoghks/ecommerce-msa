package com.ecommerce.order.exception;

/** 반품이 없거나 타인 소유일 때 → 404 (정보 노출 방지) */
public class ReturnRequestNotFoundException extends RuntimeException {

    public ReturnRequestNotFoundException(Long returnId) {
        super("반품 요청을 찾을 수 없습니다. id=" + returnId);
    }
}
