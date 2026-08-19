package com.ecommerce.order.domain;

/**
 * 결제 상태 (payment-foundation §7).
 * READY → APPROVED → CANCELED / READY → FAILED
 */
public enum PaymentStatus {

    /** 승인 요청 직전 — 내부 검증(소유자·금액)까지 통과한 상태 */
    READY,

    /** 승인 완료 — PG 승인 또는 0원 결제 내부 승인(§11-3) */
    APPROVED,

    /** 전액 취소·환불 완료 */
    CANCELED,

    /** 승인 실패 — 주문은 CANCELLED 로 보상된다 */
    FAILED
}
