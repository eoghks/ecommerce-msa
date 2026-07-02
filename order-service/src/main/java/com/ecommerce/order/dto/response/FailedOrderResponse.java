package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.FailedOrderLog;

import java.time.LocalDateTime;

/**
 * 실패 주문 조회 응답 (M-3, ADMIN).
 * 일반화된 사유만 노출 — 내부 URL/스택트레이스 등 민감정보 미포함.
 */
public record FailedOrderResponse(
        Long orderId,
        Long userId,
        String reason,
        LocalDateTime occurredAt
) {
    public static FailedOrderResponse from(FailedOrderLog log) {
        return new FailedOrderResponse(
                log.getOrderId(),
                log.getUserId(),
                log.getReason(),
                log.getOccurredAt()
        );
    }
}
