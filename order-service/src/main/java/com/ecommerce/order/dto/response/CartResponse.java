package com.ecommerce.order.dto.response;

import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        long totalPrice,
        int totalCount
) {
    public static CartResponse of(List<CartItemResponse> items) {
        long totalPrice = items.stream()
                .mapToLong(i -> i.price() * i.quantity())
                .sum();
        int totalCount = items.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();
        return new CartResponse(items, totalPrice, totalCount);
    }
}
