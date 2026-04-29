package com.ecommerce.common.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka 이벤트 공통 베이스 — Saga Choreography 에서 모든 도메인 이벤트가 상속
 *
 * 사용 예:
 *   public class OrderCreatedEvent extends BaseEvent {
 *       public OrderCreatedEvent() { super("ORDER_CREATED"); }
 *   }
 */
@Getter
public abstract class BaseEvent {

    private final String eventId;
    private final String eventType;
    private final Instant occurredAt;

    protected BaseEvent(String eventType) {
        this.eventId    = UUID.randomUUID().toString();
        this.eventType  = eventType;
        this.occurredAt = Instant.now();
    }
}
