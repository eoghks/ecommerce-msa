package com.ecommerce.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("주문을 찾을 수 없습니다. id=" + orderId);
    }
}
