package com.ecommerce.order.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 반품 진행 상태 (V1.1-5).
 * REQUESTED → APPROVED → REFUNDED / REQUESTED → REJECTED.
 */
public enum ReturnStatus {

    REQUESTED,   // 신청 접수
    APPROVED,    // 승인(재고 복구 완료, 환불 처리 대기)
    REJECTED,    // 거부(재신청 허용)
    REFUNDED;    // 환불 완료

    /** 중복 신청 차단 대상 상태 — DB 부분 유니크 인덱스(uq_return_item_active)와 동일 집합 */
    private static final Set<ReturnStatus> ACTIVE_STATUSES =
            EnumSet.of(REQUESTED, APPROVED, REFUNDED);

    /**
     * H-2: 아직 처리가 끝나지 않은(진행 중) 반품 상태.
     * 이 상태의 반품이 걸린 주문은 구매확정(수동·자동)을 막아 "확정 = 반품 불가" 경계를 지킨다.
     * REFUNDED 는 처리 완료라 확정을 막지 않는다(막으면 부분반품 주문이 영원히 미확정으로 남는다).
     */
    private static final Set<ReturnStatus> PENDING_STATUSES =
            EnumSet.of(REQUESTED, APPROVED);

    /** 항목당 1건만 허용되는 활성 상태 목록 */
    public static Collection<ReturnStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }

    /** 처리 진행 중인 반품 상태 목록 — 구매확정 차단 기준 */
    public static Collection<ReturnStatus> pendingStatuses() {
        return PENDING_STATUSES;
    }
}
