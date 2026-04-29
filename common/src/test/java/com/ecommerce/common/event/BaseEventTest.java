package com.ecommerce.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEventTest {

    static class OrderCreatedEvent extends BaseEvent {
        OrderCreatedEvent() {
            super("ORDER_CREATED");
        }
    }

    @Test
    @DisplayName("이벤트마다 고유한 eventId 생성")
    void generates_unique_event_id() {
        OrderCreatedEvent e1 = new OrderCreatedEvent();
        OrderCreatedEvent e2 = new OrderCreatedEvent();

        assertThat(e1.getEventId()).isNotEqualTo(e2.getEventId());
    }

    @Test
    @DisplayName("eventType 올바르게 설정")
    void sets_event_type() {
        OrderCreatedEvent event = new OrderCreatedEvent();

        assertThat(event.getEventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("occurredAt 은 생성 시각으로 설정")
    void sets_occurred_at() {
        Instant before = Instant.now();
        OrderCreatedEvent event = new OrderCreatedEvent();
        Instant after = Instant.now();

        assertThat(event.getOccurredAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("eventId 는 UUID 형식")
    void event_id_is_uuid_format() {
        OrderCreatedEvent event = new OrderCreatedEvent();

        assertThat(event.getEventId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }
}
