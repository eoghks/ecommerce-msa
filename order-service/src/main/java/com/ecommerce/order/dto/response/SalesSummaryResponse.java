package com.ecommerce.order.dto.response;

import java.time.LocalDate;

/**
 * 매출 요약 응답 (V1.1-7).
 * 금액은 원 단위 long, 비율은 소수 4자리. 데이터가 없는 기간도 0으로 응답한다(null 금지).
 *
 * @param totalRevenue            유효 매출 — 집계 대상 주문의 ACTIVE 항목 합계
 * @param orderCount              집계 대상 주문 건수(CONFIRMED/PARTIALLY_CANCELLED)
 * @param averageOrderValue       평균 주문금액(AOV) = 유효 매출 / 주문수 (주문수 0이면 0)
 * @param fullyCancelledCount     전체취소(CANCELLED) 건수
 * @param partiallyCancelledCount 부분취소(PARTIALLY_CANCELLED) 건수
 * @param cancelRate              취소율 = (전체취소 + 부분취소) / PENDING 제외 전체 주문수 (분모 0이면 0)
 * @param failedOrderCount        실패(자동취소) 주문 건수
 */
public record SalesSummaryResponse(LocalDate from,
                                   LocalDate to,
                                   long totalRevenue,
                                   long orderCount,
                                   long averageOrderValue,
                                   long fullyCancelledCount,
                                   long partiallyCancelledCount,
                                   double cancelRate,
                                   long failedOrderCount) {
}
