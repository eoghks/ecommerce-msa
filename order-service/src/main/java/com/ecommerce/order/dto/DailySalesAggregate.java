package com.ecommerce.order.dto;

import java.time.LocalDate;

/** 일별 매출 집계 결과 (V1.1-7) — 데이터가 있는 날짜만 반환된다(빈 날짜는 서비스에서 0으로 채움) */
public record DailySalesAggregate(LocalDate date, long revenue, long orderCount) {
}
