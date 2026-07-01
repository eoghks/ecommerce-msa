package com.ecommerce.product.event;

import com.ecommerce.product.service.StockDecreaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order.created.DLT 컨슈머 (H-3).
 *
 * 재고 차감(order.created)이 재시도까지 실패해 DLT로 이동한 경우, 주문이 영원히 PENDING으로
 * 잠기지 않도록 보상한다. 단, 이때 <b>실제 재고 차감이 완료됐는지</b>를 멱등키로 확인해
 * over-cancel(재고는 차감됐는데 주문만 취소되는 불일치)을 방지한다:
 *   - 차감 완료됨(멱등키 존재) → stock.decreased 재발행 (성공 복구) → 주문 CONFIRMED
 *     (차감 성공 후 성공 이벤트 발행만 실패해 DLT로 온 케이스)
 *   - 차감 안 됨 → stock.decrease.failed 발행 → 주문 CANCELLED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedDltConsumer {

    private final StockEventPublisher stockEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(
            topics = "${kafka.topic.order-created:order.created}.DLT",
            groupId = "product-service-dlt",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderCreatedPayload payload) {
        Long orderId = payload.orderId();
        boolean alreadyDecreased = Boolean.TRUE.equals(
                redisTemplate.hasKey(StockDecreaseService.PROCESSED_KEY_PREFIX + orderId));

        if (alreadyDecreased) {
            // 재고 차감은 완료 — 성공 이벤트만 유실됐던 케이스 → 성공 재발행 (over-cancel 방지)
            log.warn("order.created DLT지만 재고 차감은 완료됨 → 성공 이벤트 재발행. orderId={}", orderId);
            stockEventPublisher.publishStockDecreased(orderId);
        } else {
            // 차감된 적 없음 → 취소 보상
            log.error("order.created 처리 영구 실패(DLT) — 주문 취소 보상 발행. orderId={}", orderId);
            stockEventPublisher.publishStockDecreaseFailed(
                    orderId, "재고 차감 처리 영구 실패 (DLT) — 자동 취소");
        }
    }
}
