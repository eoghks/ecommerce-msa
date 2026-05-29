package com.ecommerce.order.domain;

/**
 * Redis 게스트 장바구니 직렬화 단위.
 * key: cart:guest:{guestId} / value: JSON 배열
 */
public record CartItemRecord(
        Long productId,
        String productName,
        Long price,
        int quantity,
        String imageUrl
) {
    public CartItemRecord withQuantity(int newQuantity) {
        return new CartItemRecord(productId, productName, price, newQuantity, imageUrl);
    }

    public CartItemRecord addQuantity(int delta) {
        return new CartItemRecord(productId, productName, price, quantity + delta, imageUrl);
    }
}
