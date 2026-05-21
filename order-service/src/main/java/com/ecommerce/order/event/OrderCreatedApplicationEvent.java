package com.ecommerce.order.event;

/**
 * Spring ApplicationEvent 래퍼 — @TransactionalEventListener 연동용.
 *
 * 목적: DB 트랜잭션 커밋 이후에만 Kafka 이벤트 발행을 보장한다.
 *   - DB 롤백 시 Kafka 이벤트가 발행되지 않음 (유령 이벤트 방지)
 *   - ApplicationEventPublisher → @TransactionalEventListener(AFTER_COMMIT) → Kafka 발행
 */
public record OrderCreatedApplicationEvent(OrderCreatedEvent kafkaEvent) {}
