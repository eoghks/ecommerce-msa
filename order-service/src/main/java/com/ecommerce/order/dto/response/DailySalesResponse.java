package com.ecommerce.order.dto.response;

import com.ecommerce.order.dto.DailySalesAggregate;

import java.time.LocalDate;

/** 일별 매출 추이 응답 (V1.1-7) — 데이터 없는 날짜도 0으로 채워 그래프 끊김을 막는다 */
public record DailySalesResponse(LocalDate date, long revenue, long orderCount) {

    public static DailySalesResponse from(DailySalesAggregate aggregate) {
        return new DailySalesResponse(aggregate.date(), aggregate.revenue(), aggregate.orderCount());
    }

    /** 집계 결과가 없는 날짜 — 0으로 채운다 */
    public static DailySalesResponse empty(LocalDate date) {
        return new DailySalesResponse(date, 0L, 0L);
    }
}
