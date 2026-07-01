package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.CartItem;
import com.ecommerce.order.domain.CartItemRecord;

public record CartItemResponse(
        Long productId,
        String productName,
        Long price,
        int quantity,
        String imageUrl
) {
    public static CartItemResponse from(CartItem entity) {
        return new CartItemResponse(
                entity.getProductId(),
                entity.getProductName(),
                entity.getPrice(),
                entity.getQuantity(),
                entity.getImageUrl()
        );
    }

    public static CartItemResponse from(CartItemRecord record) {
        return new CartItemResponse(
                record.productId(),
                record.productName(),
                record.price(),
                record.quantity(),
                record.imageUrl()
        );
    }
}
