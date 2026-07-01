package com.ecommerce.product.event;

import com.ecommerce.product.service.RestockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order.item.cancelled 토픽 Consumer.
 * 주문 항목 취소 시 해당 상품 재고를 복구한다 (Saga 보상).
 *
 * 에러 처리 (KafkaConfig.DefaultErrorHandler): 1초 간격 2회 재시도 후 DLT 이동.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderItemCancelledConsumer {

    private final RestockService restockService;

    @KafkaListener(
            topics = "${kafka.topic.order-item-cancelled:order.item.cancelled}",
            groupId = "product-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderItemCancelledPayload payload) {
        log.info("항목 취소 이벤트 수신 — 재고 복구. orderId={}, itemId={}, productId={}, qty={}",
                payload.orderId(), payload.itemId(), payload.productId(), payload.quantity());
        restockService.restock(payload);
    }
}
