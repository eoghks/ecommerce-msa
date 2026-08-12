package com.ecommerce.order.dto;

/**
 * 취소 집계 결과 (V1.1-7).
 * totalCount 는 기간 내 생성된 주문 중 PENDING(미확정)을 제외한 전체 건수,
 * 취소는 전체취소(CANCELLED)와 부분취소(PARTIALLY_CANCELLED)를 분리해 제공한다.
 */
public record OrderCancelAggregate(long totalCount,
                                   long fullyCancelledCount,
                                   long partiallyCancelledCount) {
}
