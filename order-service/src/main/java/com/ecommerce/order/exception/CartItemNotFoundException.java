package com.ecommerce.order.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long productId) {
        super("장바구니에 해당 상품이 없습니다: productId=" + productId);
    }
}
