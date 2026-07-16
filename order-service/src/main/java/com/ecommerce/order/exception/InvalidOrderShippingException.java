package com.ecommerce.order.exception;

/** 주문 생성 시 배송지 정보가 유효하지 않음(선택한 addressId 무효 또는 직접입력 누락) → 400 */
public class InvalidOrderShippingException extends RuntimeException {
    public InvalidOrderShippingException(String message) {
        super(message);
    }
}
