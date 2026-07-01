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
    // HR-04: 수량 하한 1 / 상한 999 검증
    private static final int MAX_QUANTITY = 999;

    public CartItemRecord withQuantity(int newQuantity) {
        if (newQuantity < 1 || newQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1 ~ " + MAX_QUANTITY + " 사이여야 합니다.");
        }
        return new CartItemRecord(productId, productName, price, newQuantity, imageUrl);
    }

    public CartItemRecord addQuantity(int delta) {
        int next = quantity + delta;
        if (next < 1 || next > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1 ~ " + MAX_QUANTITY + " 사이여야 합니다.");
        }
        return new CartItemRecord(productId, productName, price, next, imageUrl);
    }
}
