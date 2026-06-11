package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.OrderItem;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Long price,
        Integer quantity,
        Long subtotal,
        Long sellerId,
        String status,
        String cancelReason
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getPrice(),
                item.getQuantity(),
                item.subtotal(),
                item.getSellerId(),
                item.getStatus().name(),
                item.getCancelReason()
        );
    }
}
