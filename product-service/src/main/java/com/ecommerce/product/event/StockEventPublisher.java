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
        sendWithLogging(stockDecreasedTopic, orderId, new StockDecreasedEvent(orderId), "재고 차감 성공");
    }

    /** 재고 차감 실패 — Order Service 주문 CANCELLED 트리거 (보상 트랜잭션) */
    public void publishStockDecreaseFailed(Long orderId, String reason) {
        sendWithLogging(stockDecreaseFailedTopic, orderId, new StockDecreaseFailedEvent(orderId, reason), "재고 차감 실패");
    }

    /** L-02: 공통 Kafka 발행 + 결과 로깅 (중복 제거) */
    private void sendWithLogging(String topic, Long orderId, Object event, String label) {
        kafkaTemplate.send(topic, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("{} 이벤트 발행 실패 — 수동 개입 필요. orderId={}", label, orderId, ex);
                    } else {
                        log.info("{} 이벤트 발행 완료. orderId={}", label, orderId);
                    }
                });
    }
}
