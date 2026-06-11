package com.ecommerce.order.exception;

public class OrderItemNotFoundException extends RuntimeException {
    public OrderItemNotFoundException(Long itemId) {
        super("주문 항목을 찾을 수 없습니다: itemId=" + itemId);
    }
}
