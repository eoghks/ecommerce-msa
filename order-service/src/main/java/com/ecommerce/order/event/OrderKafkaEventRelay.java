package com.ecommerce.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB 트랜잭션 커밋 후 Kafka 이벤트 릴레이.
 *
 * AFTER_COMMIT 보장:
 *   - DB 커밋 성공 → Kafka 발행 (정상 흐름)
 *   - DB 롤백 → 리스너 실행 안 됨 → Kafka 미발행 (유령 이벤트 방지)
 *
 * 면접 포인트:
 *   이 방식의 한계: AFTER_COMMIT 이후 Kafka 발행이 실패하면 이벤트 유실 가능.
 *   완전한 At-Least-Once 보장은 Outbox Pattern 필요 (docs/decisions/outbox-pattern.md 참조).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaEventRelay {

    private final OrderEventPublisher orderEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedApplicationEvent applicationEvent) {
        log.info("트랜잭션 커밋 후 Kafka 이벤트 발행. orderId={}",
                applicationEvent.kafkaEvent().getOrderId());
        orderEventPublisher.publishOrderCreated(applicationEvent.kafkaEvent());
    }
}
