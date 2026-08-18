package com.ecommerce.order.dto.response;

import com.ecommerce.order.dto.DailyCountAggregate;

import java.time.LocalDate;

/** 실패(자동취소) 주문 일별 추이 응답 (V1.1-7) — 데이터 없는 날짜도 0으로 채운다 */
public record FailedOrderTrendResponse(LocalDate date, long count) {

    public static FailedOrderTrendResponse from(DailyCountAggregate aggregate) {
        return new FailedOrderTrendResponse(aggregate.date(), aggregate.count());
    }

    public static FailedOrderTrendResponse empty(LocalDate date) {
        return new FailedOrderTrendResponse(date, 0L);
    }
}
