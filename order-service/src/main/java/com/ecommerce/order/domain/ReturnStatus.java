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

    /** 항목당 1건만 허용되는 활성 상태 목록 */
    public static Collection<ReturnStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }
}
