package com.ecommerce.common.event;

import lombok.Getter;

/**
 * 재고 차감 실패 이벤트 — Product Service 발행, Order Service 수신
 * Order Service: 주문 상태 CANCELLED 처리 (보상 트랜잭션)
 */
@Getter
public class StockDecreaseFailedEvent extends BaseEvent {

    private final Long orderId;
    private final String reason;

    public StockDecreaseFailedEvent(Long orderId, String reason) {
        super("STOCK_DECREASE_FAILED");
        this.orderId = orderId;
        this.reason  = reason;
    }

    /** Jackson 역직렬화용 기본 생성자 */
    protected StockDecreaseFailedEvent() {
        super("STOCK_DECREASE_FAILED");
        this.orderId = null;
        this.reason  = null;
    }
}
