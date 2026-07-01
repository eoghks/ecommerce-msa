package com.ecommerce.order.exception;

public class OrderItemAccessDeniedException extends RuntimeException {
    public OrderItemAccessDeniedException(Long itemId) {
        super("본인이 판매하는 상품 항목만 취소할 수 있습니다: itemId=" + itemId);
    }
}
