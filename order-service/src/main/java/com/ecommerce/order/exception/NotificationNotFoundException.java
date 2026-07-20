package com.ecommerce.order.exception;

/** 알림이 없거나 타인 소유일 때 → 404 (정보 노출 방지) */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("알림을 찾을 수 없습니다. id=" + notificationId);
    }
}
