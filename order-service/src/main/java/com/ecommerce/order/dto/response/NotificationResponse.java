package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.Notification;
import com.ecommerce.order.domain.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림 조회 응답 (V1.1-4).
 * orderId 가 있으면 프론트에서 주문 상세로 이동.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getOrderId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
