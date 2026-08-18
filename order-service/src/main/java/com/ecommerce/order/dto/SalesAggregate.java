package com.ecommerce.order.dto;

/**
 * 매출 집계 결과 (V1.1-7) — DB 집계 쿼리 1회의 projection.
 * revenue 는 집계 대상 주문의 ACTIVE 항목 `price * quantity` 합계(유효 매출),
 * orderCount 는 해당 주문 건수다.
 */
public record SalesAggregate(long revenue, long orderCount) {
}
