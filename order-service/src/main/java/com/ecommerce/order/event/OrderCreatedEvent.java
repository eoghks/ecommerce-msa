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
        // L-03: 방어적 복사 — 외부 리스트 변경으로 이벤트 상태가 오염되는 것을 방지
        this.items   = List.copyOf(items);
    }
}
