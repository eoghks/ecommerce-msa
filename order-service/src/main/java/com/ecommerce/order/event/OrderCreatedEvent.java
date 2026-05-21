package com.ecommerce.order.event;

import com.ecommerce.common.event.BaseEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final Long orderId;
    private final Long userId;
    private final List<OrderItemPayload> items;

    public OrderCreatedEvent(Long orderId, Long userId, List<OrderItemPayload> items) {
        super("ORDER_CREATED");
        this.orderId = orderId;
        this.userId  = userId;
        this.items   = items;
    }
}
