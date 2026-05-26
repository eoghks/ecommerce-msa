package com.ecommerce.product.event;

import com.ecommerce.product.service.StockDecreaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order.created 토픽 Consumer.
 * 수신 후 Redis Lock + 재고 차감 → 결과 이벤트 발행 (Saga Choreography).
 *
 * 에러 처리 (KafkaConfig.DefaultErrorHandler):
 *   - 예외 발생 시 1초 간격 2회 재시도
 *   - 3회 모두 실패 → order.created.DLT 로 이동
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventConsumer {

    private final StockDecreaseService stockDecreaseService;

    @KafkaListener(
            topics = "${kafka.topic.order-created:order.created}",
            groupId = "product-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderCreatedPayload payload) {
        log.info("주문 생성 이벤트 수신. orderId={}, itemCount={}",
                payload.orderId(), payload.items().size());
        stockDecreaseService.decreaseStock(payload);
    }
}
