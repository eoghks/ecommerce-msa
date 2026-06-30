package com.ecommerce.product.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * order.item.cancelled 토픽 수신용 Consumer DTO.
 * OrderItemCancelledEvent JSON 구조와 매핑 — 필요한 필드만 선언.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemCancelledPayload(
        Long orderId,
        Long itemId,
        Long productId,
        Integer quantity
) {}
