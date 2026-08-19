package com.ecommerce.order.dto;

/**
 * 결제 미완료 만료 대상 주문 (payment-foundation §11.1, M-07).
 * 만료 사유 기록(FailedOrderLog)에 필요한 최소 정보(주문 id·소유자)만 조회해 엔티티 로딩 비용을 줄인다.
 */
public record PaymentExpireTarget(Long orderId, Long userId) {
}
