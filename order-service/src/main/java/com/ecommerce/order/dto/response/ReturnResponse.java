package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;

import java.time.LocalDateTime;

/** 반품 조회 응답 (V1.1-5) */
public record ReturnResponse(
        Long id,
        Long orderId,
        Long orderItemId,
        Long userId,
        String reason,
        ReturnStatus status,
        String rejectReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static ReturnResponse from(ReturnRequest returnRequest) {
        return new ReturnResponse(
                returnRequest.getId(),
                returnRequest.getOrderId(),
                returnRequest.getOrderItemId(),
                returnRequest.getUserId(),
                returnRequest.getReason(),
                returnRequest.getStatus(),
                returnRequest.getRejectReason(),
                returnRequest.getRequestedAt(),
                returnRequest.getProcessedAt()
        );
    }
}
