package com.ecommerce.order.dto;

/**
 * 자동 구매확정 대상 주문 (payment-foundation §3.2).
 * 알림 발송에 필요한 최소 정보(주문 id·소유자)만 조회해 엔티티 로딩 비용을 줄인다.
 */
public record AutoConfirmTarget(Long orderId, Long userId) {
}
