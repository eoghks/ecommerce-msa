package com.ecommerce.order.support;

/**
 * PG 주문번호 규칙 (payment-foundation §11-1).
 * 토스페이먼츠는 orderId 를 6~64자 문자열로 요구하므로 내부 주문 id 를 그대로 쓸 수 없다.
 * 규칙을 결정적으로 두어 서버가 항상 내부 주문 id 로부터 재생성해 검증한다(클라이언트 값 신뢰 금지).
 * 프론트(`frontend/src/utils/pgOrderId.ts`)도 동일 규칙을 사용해야 승인이 성립한다.
 */
public final class PgOrderId {

    /** ORD- + 8자리 zero-pad (총 12자) — 토스 최소 길이(6자) 충족 */
    private static final String FORMAT = "ORD-%08d";

    private PgOrderId() {
    }

    public static String of(Long orderId) {
        return String.format(FORMAT, orderId);
    }
}
