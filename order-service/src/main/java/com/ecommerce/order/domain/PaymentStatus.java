package com.ecommerce.order.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 결제 상태 (payment-foundation §7).
 * READY → APPROVED → CANCELED / READY → FAILED / READY → UNKNOWN(결과 미확정, C-01)
 */
public enum PaymentStatus {

    /** 승인 요청 직전 — 내부 검증(소유자·금액)까지 통과한 상태 */
    READY,

    /** 승인 완료 — PG 승인 또는 0원 결제 내부 승인(§11-3) */
    APPROVED,

    /** 전액 취소·환불 완료 */
    CANCELED,

    /** 승인 실패 — 주문은 CANCELLED 로 보상된다 */
    FAILED,

    /**
     * C-01: 승인 결과 미확정 — PG 통신 실패 후 재조회로도 승인 여부를 판정하지 못한 상태.
     * 이미 출금됐을 수 있으므로 조회·환불 경로에서 배제하지 않고(FAILED 로 덮지 않고) 남겨
     * 운영자가 반드시 찾을 수 있게 한다.
     */
    UNKNOWN;

    /**
     * C-02: 진행 중·미확정 결제 — "결제가 아직 살아 있는" 상태 집합.
     * 주문 만료 제외·재결제 차단 판정의 공통 기준이며, 이 상태의 결제가 있는 주문은 만료시키지 않는다
     * (PG 왕복 중 만료시키면 승인된 돈이 주문 취소로 덮여 환불 경로가 사라진다).
     */
    private static final Set<PaymentStatus> IN_FLIGHT_STATUSES = EnumSet.of(READY, UNKNOWN);

    /** 진행 중·미확정 결제 상태 목록 — 도메인·조회·조건부 UPDATE 공통 기준 */
    public static Collection<PaymentStatus> inFlightStatuses() {
        return IN_FLIGHT_STATUSES;
    }
}
