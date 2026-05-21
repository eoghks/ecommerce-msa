package com.ecommerce.product.event;

import com.ecommerce.common.event.StockDecreaseFailedEvent;
import com.ecommerce.common.event.StockDecreasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.stock-decreased:stock.decreased}")
    private String stockDecreasedTopic;

    @Value("${kafka.topic.stock-decrease-failed:stock.decrease.failed}")
    private String stockDecreaseFailedTopic;

    /** 재고 차감 성공 — Order Service 주문 CONFIRMED 트리거 */
    public void publishStockDecreased(Long orderId) {
        StockDecreasedEvent event = new StockDecreasedEvent(orderId);
        kafkaTemplate.send(stockDecreasedTopic, String.valueOf(orderId), event);
        log.info("재고 차감 성공 이벤트 발행. orderId={}", orderId);
    }

    /** 재고 차감 실패 — Order Service 주문 CANCELLED 트리거 (보상 트랜잭션) */
    public void publishStockDecreaseFailed(Long orderId, String reason) {
        StockDecreaseFailedEvent event = new StockDecreaseFailedEvent(orderId, reason);
        kafkaTemplate.send(stockDecreaseFailedTopic, String.valueOf(orderId), event);
        log.warn("재고 차감 실패 이벤트 발행. orderId={}, reason={}", orderId, reason);
    }
}
