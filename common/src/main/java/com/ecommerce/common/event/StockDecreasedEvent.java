package com.ecommerce.common.event;

import lombok.Getter;

/**
 * 재고 차감 성공 이벤트 — Product Service 발행, Order Service 수신
 * Order Service: 주문 상태 CONFIRMED 처리
 */
@Getter
public class StockDecreasedEvent extends BaseEvent {

    private final Long orderId;

    public StockDecreasedEvent(Long orderId) {
        super("STOCK_DECREASED");
        this.orderId = orderId;
    }

    /** Jackson 역직렬화용 기본 생성자 */
    protected StockDecreasedEvent() {
        super("STOCK_DECREASED");
        this.orderId = null;
    }
}
