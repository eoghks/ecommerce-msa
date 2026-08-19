package com.ecommerce.order.event;

import com.ecommerce.order.domain.Order;

/**
 * Spring ApplicationEvent 래퍼 — @TransactionalEventListener 연동용.
 *
 * 목적: DB 트랜잭션 커밋 이후에만 Kafka 이벤트 발행을 보장한다.
 *   - DB 롤백 시 Kafka 이벤트가 발행되지 않음 (유령 이벤트 방지)
 *   - ApplicationEventPublisher → @TransactionalEventListener(AFTER_COMMIT) → Kafka 발행
 */
public record OrderCreatedApplicationEvent(OrderCreatedEvent kafkaEvent) {

    /**
     * 결제 완료(PENDING 전이) 주문으로 재고 차감 Saga 시작 이벤트를 만든다 (payment-foundation §5).
     * 발행 시점이 "주문 저장"에서 "결제 승인"으로 옮겨졌으므로(승인 전 재고 미차감) 생성 지점을 한 곳으로 모은다.
     */
    public static OrderCreatedApplicationEvent from(Order order) {
        return new OrderCreatedApplicationEvent(new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getItems().stream()
                        .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                        .toList()));
    }
}
