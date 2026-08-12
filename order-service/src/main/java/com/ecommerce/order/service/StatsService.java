package com.ecommerce.order.service;

import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.DailyCountAggregate;
import com.ecommerce.order.dto.DailySalesAggregate;
import com.ecommerce.order.dto.OrderCancelAggregate;
import com.ecommerce.order.dto.SalesAggregate;
import com.ecommerce.order.dto.StatsPeriod;
import com.ecommerce.order.dto.response.DailySalesResponse;
import com.ecommerce.order.dto.response.FailedOrderTrendResponse;
import com.ecommerce.order.dto.response.ProductSalesResponse;
import com.ecommerce.order.dto.response.SalesSummaryResponse;
import com.ecommerce.order.dto.response.SellerSalesResponse;
import com.ecommerce.order.repository.StatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 매출 통계 서비스 (V1.1-7).
 *
 * 매출 기준(설계 3장):
 * - 유효 매출 = 집계 대상 주문(CONFIRMED/PARTIALLY_CANCELLED)의 ACTIVE 항목 `price * quantity` 합계
 * - 주문의 total_price 는 생성 시점 금액이라 부분취소가 반영되지 않아 사용하지 않는다.
 * - 취소율 분모는 PENDING 제외 전체 주문수, 취소는 전체취소/부분취소를 분리 제공한다.
 *
 * 집계는 전부 DB 쿼리에서 수행하고 서비스는 조립·빈 날짜 채움만 담당한다.
 * V1.2-7 판매자 센터 인사이트는 각 조회 메서드에 판매자 필터를 추가해 이 로직을 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    /** 유효 매출 집계 대상 주문 상태 — 재고가 실제 차감된 주문만(구매 인증과 동일 기준) */
    private static final Set<OrderStatus> REVENUE_ORDER_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    /** 매출에 포함하는 항목 상태 — 취소된 항목은 제외 */
    private static final OrderItemStatus ACTIVE_ITEM_STATUS = OrderItemStatus.ACTIVE;

    /** 비율(취소율) 소수 자리수 */
    private static final int RATE_SCALE = 4;

    private final StatsRepository statsRepository;

    /** 기간 요약 지표 — 데이터가 없어도 0으로 채워 응답한다 */
    public SalesSummaryResponse getSummary(StatsPeriod period) {
        SalesAggregate sales = aggregateSales(period);
        OrderCancelAggregate cancels = statsRepository.aggregateOrderCancels(
                period.fromAt(), period.toAtExclusive(),
                OrderStatus.CANCELLED, OrderStatus.PARTIALLY_CANCELLED, OrderStatus.PENDING);
        long failedOrderCount = statsRepository.countFailedOrders(period.fromAt(), period.toAtExclusive());

        return new SalesSummaryResponse(
                period.from(), period.to(),
                sales.revenue(), sales.orderCount(), averageOrderValue(sales),
                cancels.fullyCancelledCount(), cancels.partiallyCancelledCount(),
                cancelRate(cancels), failedOrderCount);
    }

    /** 일별 매출 추이 — 데이터 없는 날짜는 0으로 채우고 날짜 오름차순으로 반환 */
    public List<DailySalesResponse> getDailySales(StatsPeriod period) {
        Map<LocalDate, DailySalesAggregate> byDate = statsRepository.aggregateDailySales(
                        period.fromAt(), period.toAtExclusive(),
                        REVENUE_ORDER_STATUSES, ACTIVE_ITEM_STATUS).stream()
                .collect(Collectors.toMap(DailySalesAggregate::date, Function.identity()));

        return period.dates().stream()
                .map(date -> Optional.ofNullable(byDate.get(date))
                        .map(DailySalesResponse::from)
                        .orElseGet(() -> DailySalesResponse.empty(date)))
                .toList();
    }

    /** 상품별 매출 Top N — 취소 항목 제외, 매출 내림차순 */
    public List<ProductSalesResponse> getTopProducts(StatsPeriod period, int limit) {
        return statsRepository.findTopProducts(period.fromAt(), period.toAtExclusive(),
                REVENUE_ORDER_STATUSES, ACTIVE_ITEM_STATUS, PageRequest.ofSize(limit));
    }

    /** 판매자별 매출 Top N — 취소 항목 제외, 매출 내림차순 */
    public List<SellerSalesResponse> getTopSellers(StatsPeriod period, int limit) {
        return statsRepository.findTopSellers(period.fromAt(), period.toAtExclusive(),
                REVENUE_ORDER_STATUSES, ACTIVE_ITEM_STATUS, PageRequest.ofSize(limit));
    }

    /** 실패(자동취소) 주문 일별 추이 — 데이터 없는 날짜는 0으로 채움 */
    public List<FailedOrderTrendResponse> getFailedOrderTrend(StatsPeriod period) {
        Map<LocalDate, DailyCountAggregate> byDate = statsRepository.aggregateDailyFailedOrders(
                        period.fromAt(), period.toAtExclusive()).stream()
                .collect(Collectors.toMap(DailyCountAggregate::date, Function.identity()));

        return period.dates().stream()
                .map(date -> Optional.ofNullable(byDate.get(date))
                        .map(FailedOrderTrendResponse::from)
                        .orElseGet(() -> FailedOrderTrendResponse.empty(date)))
                .toList();
    }

    private SalesAggregate aggregateSales(StatsPeriod period) {
        return statsRepository.aggregateSales(period.fromAt(), period.toAtExclusive(),
                REVENUE_ORDER_STATUSES, ACTIVE_ITEM_STATUS);
    }

    /** 평균 주문금액 — 주문수 0이면 0 (0으로 나누기 방지) */
    private long averageOrderValue(SalesAggregate sales) {
        if (sales.orderCount() == 0) {
            return 0L;
        }
        return sales.revenue() / sales.orderCount();
    }

    /** 취소율 = (전체취소 + 부분취소) / PENDING 제외 전체 주문수, 소수 4자리. 분모 0이면 0 */
    private double cancelRate(OrderCancelAggregate cancels) {
        if (cancels.totalCount() == 0) {
            return 0d;
        }
        long cancelled = cancels.fullyCancelledCount() + cancels.partiallyCancelledCount();
        return BigDecimal.valueOf(cancelled)
                .divide(BigDecimal.valueOf(cancels.totalCount()), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
