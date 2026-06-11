package com.ecommerce.order.domain;

/**
 * 주문 항목 상태.
 * ACTIVE: 정상 / CANCELLED: 부분 취소됨 (판매자 또는 관리자가 항목 단위 취소)
 */
public enum OrderItemStatus {
    ACTIVE,
    CANCELLED
}
