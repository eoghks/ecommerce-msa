package com.ecommerce.order.event;

import com.ecommerce.common.event.StockDecreaseFailedEvent;
import com.ecommerce.common.event.StockDecreasedEvent;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 재고 이벤트 Consumer — Saga Choreography 보상 처리.
 *
 * stock.decreased      → 주문 CONFIRMED
 * stock.decrease.failed → 주문 CANCELLED (보상 트랜잭션)
 *
 * 에러 처리 (KafkaConfig.DefaultErrorHandler):
 *   - 예외 발생 시 1초 간격 2회 재시도
 *   - 3회 모두 실패 → {topic}.DLT 로 이동 → 운영자 알림 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private final OrderService orderService;

    /** 재고 차감 성공 — 주문 CONFIRMED */
    @KafkaListener(
            topics = "${kafka.topic.stock-decreased:stock.decreased}",
            groupId = "order-service"
    )
    public void onStockDecreased(StockDecreasedEvent event) {
        log.info("재고 차감 성공 이벤트 수신. orderId={}", event.getOrderId());
        orderService.confirmOrder(event.getOrderId());
    }

    /** 재고 차감 실패 — 주문 CANCELLED (보상 트랜잭션) */
    @KafkaListener(
            topics = "${kafka.topic.stock-decrease-failed:stock.decrease.failed}",
            groupId = "order-service"
    )
    public void onStockDecreaseFailed(StockDecreaseFailedEvent event) {
        log.warn("재고 차감 실패 이벤트 수신. orderId={}, reason={}", event.getOrderId(), event.getReason());
        orderService.cancelOrder(event.getOrderId(), event.getReason());
    }
}
