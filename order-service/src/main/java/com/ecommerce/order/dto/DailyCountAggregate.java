package com.ecommerce.order.dto;

import java.time.LocalDate;

/** 일별 건수 집계 결과 (V1.1-7) — 실패주문 추이 등 단순 카운트 추이용 */
public record DailyCountAggregate(LocalDate date, long count) {
}
