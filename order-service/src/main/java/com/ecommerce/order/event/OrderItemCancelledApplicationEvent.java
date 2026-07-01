package com.ecommerce.order.event;

/**
 * Spring ApplicationEvent 래퍼 — 항목 취소 트랜잭션 커밋 후에만 Kafka 발행을 보장.
 * (OrderCreatedApplicationEvent와 동일 패턴)
 */
public record OrderItemCancelledApplicationEvent(OrderItemCancelledEvent kafkaEvent) {}
