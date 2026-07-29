package com.ecommerce.order.domain;

import lombok.Getter;

import java.util.Optional;

/**
 * 알림 타입 (V1.1-4). 타입별 제목·메시지 템플릿을 함께 보유한다.
 * message 템플릿은 주문번호 수준만 노출 — 개인정보 미포함.
 */
@Getter
public enum NotificationType {

    ORDER_CONFIRMED("주문 확정", "주문이 확정되었습니다. (주문 #%d)"),
    ORDER_CANCELLED("주문 취소", "주문이 취소되었습니다. (주문 #%d)"),
    ORDER_ITEM_CANCELLED("주문 항목 취소", "주문 항목이 취소되었습니다. (주문 #%d)"),
    DELIVERY_SHIPPING("배송 시작", "상품 배송이 시작되었습니다. (주문 #%d)"),
    DELIVERY_DELIVERED("배송 완료", "상품이 배송 완료되었습니다. (주문 #%d)"),
    RETURN_APPROVED("반품 승인", "반품 신청이 승인되었습니다. (주문 #%d)"),
    RETURN_REJECTED("반품 거부", "반품 신청이 거부되었습니다. (주문 #%d)"),
    RETURN_REFUNDED("환불 완료", "반품 환불이 완료되었습니다. (주문 #%d)");

    private final String title;
    private final String messageTemplate;

    NotificationType(String title, String messageTemplate) {
        this.title = title;
        this.messageTemplate = messageTemplate;
    }

    /** 주문번호를 채운 알림 메시지 조립 */
    public String formatMessage(Long orderId) {
        return String.format(messageTemplate, orderId);
    }

    /** 배송상태 전이에 대응하는 알림 타입 (준비중 등 대상 아님이면 empty) */
    public static Optional<NotificationType> fromDeliveryStatus(DeliveryStatus status) {
        return switch (status) {
            case SHIPPING  -> Optional.of(DELIVERY_SHIPPING);
            case DELIVERED -> Optional.of(DELIVERY_DELIVERED);
            default        -> Optional.empty();
        };
    }
}
