package com.ecommerce.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.order-created:order.created}")
    private String orderCreatedTopic;

    @Value("${kafka.topic.order-item-cancelled:order.item.cancelled}")
    private String orderItemCancelledTopic;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(orderCreatedTopic, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("OrderCreatedEvent 발행 실패. orderId={}", event.getOrderId(), ex);
                    } else {
                        log.info("OrderCreatedEvent 발행 완료. orderId={}, topic={}",
                                event.getOrderId(), orderCreatedTopic);
                    }
                });
    }

    /** 항목 취소 → 재고 복구 트리거 (Product Service 수신) */
    public void publishOrderItemCancelled(OrderItemCancelledEvent event) {
        // 파티션 키를 productId로 → 동일 상품 재고 이벤트 순서 보장
        kafkaTemplate.send(orderItemCancelledTopic, String.valueOf(event.getProductId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("OrderItemCancelledEvent 발행 실패 — 수동 개입 필요. orderId={}, itemId={}",
                                event.getOrderId(), event.getItemId(), ex);
                    } else {
                        log.info("OrderItemCancelledEvent 발행 완료. orderId={}, itemId={}, productId={}",
                                event.getOrderId(), event.getItemId(), event.getProductId());
                    }
                });
    }
}
