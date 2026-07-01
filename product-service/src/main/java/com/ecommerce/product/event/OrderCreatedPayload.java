package com.ecommerce.product.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * order.created 토픽 수신용 Consumer DTO.
 * OrderCreatedEvent JSON 구조와 매핑 — 필요한 필드만 선언.
 * @JsonIgnoreProperties: 알 수 없는 필드(eventId, occurredAt 등) 무시.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedPayload(
        Long orderId,
        Long userId,
        List<Item> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productId, Integer quantity) {}
}
