package com.ecommerce.order.event;

import com.ecommerce.common.event.BaseEvent;
import lombok.Getter;

/**
 * 주문 항목 취소 이벤트 — Order Service 발행, Product Service 수신.
 * Product Service: 취소된 항목의 수량만큼 재고 복구 (increaseStock).
 */
@Getter
public class OrderItemCancelledEvent extends BaseEvent {

    private final Long orderId;
    private final Long itemId;
    private final Long productId;
    private final Integer quantity;

    public OrderItemCancelledEvent(Long orderId, Long itemId, Long productId, Integer quantity) {
        super("ORDER_ITEM_CANCELLED");
        this.orderId   = orderId;
        this.itemId    = itemId;
        this.productId = productId;
        this.quantity  = quantity;
    }
}
