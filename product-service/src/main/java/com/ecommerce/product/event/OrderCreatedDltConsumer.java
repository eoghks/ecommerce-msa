package com.ecommerce.product.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order.created.DLT 컨슈머 (H-3).
 *
 * 재고 차감(order.created)이 인프라 예외 등으로 재시도까지 모두 실패해 DLT로 이동한 경우,
 * 주문이 영원히 PENDING 으로 잠기는 것을 막기 위해 보상 이벤트(stock.decrease.failed)를 발행한다.
 * → Order Service 가 해당 주문을 CANCELLED 처리.
 *
 * 비즈니스 실패(재고 부족)는 StockDecreaseService 에서 직접 보상 이벤트를 발행하므로
 * 이 DLT 경로에는 인프라성 영구 실패만 도달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedDltConsumer {

    private final StockEventPublisher stockEventPublisher;

    @KafkaListener(
            topics = "${kafka.topic.order-created:order.created}.DLT",
            groupId = "product-service-dlt",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderCreatedPayload payload) {
        log.error("order.created 처리 영구 실패(DLT) — 주문 취소 보상 발행. orderId={}, itemCount={}",
                payload.orderId(), payload.items() == null ? 0 : payload.items().size());
        stockEventPublisher.publishStockDecreaseFailed(
                payload.orderId(), "재고 차감 처리 영구 실패 (DLT) — 자동 취소");
    }
}
