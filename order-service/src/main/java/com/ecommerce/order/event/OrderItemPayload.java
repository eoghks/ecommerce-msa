package com.ecommerce.order.event;

import lombok.Getter;

@Getter
public class OrderItemPayload {

    private final Long productId;
    private final Integer quantity;

    public OrderItemPayload(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity  = quantity;
    }
}
