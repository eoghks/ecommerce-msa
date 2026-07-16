package com.ecommerce.order.exception;

/** 잘못된 배송상태 전이(역행·건너뜀·동일) 또는 배송상태 변경 대상이 아닌 주문 → 400 */
public class InvalidDeliveryStatusException extends RuntimeException {
    public InvalidDeliveryStatusException(String message) {
        super(message);
    }
}
