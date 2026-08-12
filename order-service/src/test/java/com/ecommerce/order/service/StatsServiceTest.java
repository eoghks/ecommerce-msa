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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService 매출 통계 단위 테스트 (V1.1-7)")
class StatsServiceTest {

    @InjectMocks private StatsService    statsService;
    @Mock        private StatsRepository statsRepository;

    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 7, 3);

    private final StatsPeriod period = StatsPeriod.of(FROM, TO);

    // ── 요약 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("요약 — AOV는 유효매출/주문수, 취소율은 (전체취소+부분취소)/PENDING 제외 전체")
    void getSummary_calculatesAovAndCancelRate() {
        givenSales(1_200_000L, 7L);
        givenCancels(12L, 3L, 2L);
        given(statsRepository.countFailedOrders(period.fromAt(), period.toAtExclusive())).willReturn(4L);

        SalesSummaryResponse summary = statsService.getSummary(period);

        assertThat(summary.from()).isEqualTo(FROM);
        assertThat(summary.to()).isEqualTo(TO);
        assertThat(summary.totalRevenue()).isEqualTo(1_200_000L);
        assertThat(summary.orderCount()).isEqualTo(7L);
        assertThat(summary.averageOrderValue()).isEqualTo(171_428L);   // 1,200,000 / 7 (정수 절삭)
        assertThat(summary.fullyCancelledCount()).isEqualTo(3L);
        assertThat(summary.partiallyCancelledCount()).isEqualTo(2L);
        assertThat(summary.cancelRate()).isEqualTo(0.4167);            // 5/12, 소수 4자리
        assertThat(summary.failedOrderCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("요약 — 데이터가 없으면 모든 지표가 0 (0으로 나누기 방지)")
    void getSummary_noData_returnsZeros() {
        givenSales(0L, 0L);
        givenCancels(0L, 0L, 0L);
        given(statsRepository.countFailedOrders(period.fromAt(), period.toAtExclusive())).willReturn(0L);

        SalesSummaryResponse summary = statsService.getSummary(period);

        assertThat(summary.totalRevenue()).isZero();
        assertThat(summary.orderCount()).isZero();
        assertThat(summary.averageOrderValue()).isZero();
        assertThat(summary.cancelRate()).isZero();
        assertThat(summary.failedOrderCount()).isZero();
    }

    @Test
    @DisplayName("요약 — 취소가 없으면 취소율 0, 전부 취소면 1.0")
    void getSummary_cancelRateEdges() {
        givenSales(0L, 0L);
        givenCancels(5L, 0L, 0L);
        given(statsRepository.countFailedOrders(period.fromAt(), period.toAtExclusive())).willReturn(0L);
        assertThat(statsService.getSummary(period).cancelRate()).isZero();

        givenCancels(5L, 4L, 1L);
        assertThat(statsService.getSummary(period).cancelRate()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("요약 — 집계 대상 상태는 CONFIRMED/PARTIALLY_CANCELLED, 항목은 ACTIVE 만 조회한다")
    void getSummary_usesRevenueStatusesAndActiveItems() {
        givenSales(1_000L, 1L);
        givenCancels(1L, 0L, 0L);
        given(statsRepository.countFailedOrders(period.fromAt(), period.toAtExclusive())).willReturn(0L);

        statsService.getSummary(period);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        then(statsRepository).should().aggregateSales(eq(period.fromAt()), eq(period.toAtExclusive()),
                statuses.capture(), eq(OrderItemStatus.ACTIVE));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED)
                .doesNotContain(OrderStatus.PENDING, OrderStatus.CANCELLED);

        // 취소율 분모에서 PENDING 을 제외하고, 전체취소/부분취소를 분리 집계한다
        then(statsRepository).should().aggregateOrderCancels(period.fromAt(), period.toAtExclusive(),
                OrderStatus.CANCELLED, OrderStatus.PARTIALLY_CANCELLED, OrderStatus.PENDING);
    }

    // ── 일별 추이 ────────────────────────────────────────────────

    @Test
    @DisplayName("일별 추이 — 데이터 없는 날짜는 0으로 채우고 날짜 오름차순으로 반환")
    void getDailySales_fillsEmptyDatesWithZero() {
        given(statsRepository.aggregateDailySales(eq(period.fromAt()), eq(period.toAtExclusive()),
                anyCollection(), eq(OrderItemStatus.ACTIVE)))
                .willReturn(List.of(
                        new DailySalesAggregate(TO, 3_000L, 2L),        // 정렬 확인용으로 역순 제공
                        new DailySalesAggregate(FROM, 1_000L, 1L)));

        List<DailySalesResponse> daily = statsService.getDailySales(period);

        assertThat(daily).extracting(DailySalesResponse::date)
                .containsExactly(FROM, FROM.plusDays(1), TO);
        assertThat(daily).extracting(DailySalesResponse::revenue)
                .containsExactly(1_000L, 0L, 3_000L);
        assertThat(daily).extracting(DailySalesResponse::orderCount)
                .containsExactly(1L, 0L, 2L);
    }

    @Test
    @DisplayName("일별 추이 — 데이터가 전혀 없어도 기간 전체를 0으로 채워 반환")
    void getDailySales_noData_allZero() {
        given(statsRepository.aggregateDailySales(any(), any(), anyCollection(), any()))
                .willReturn(List.of());

        List<DailySalesResponse> daily = statsService.getDailySales(period);

        assertThat(daily).hasSize(3);
        assertThat(daily).allMatch(row -> row.revenue() == 0L && row.orderCount() == 0L);
    }

    // ── Top N ───────────────────────────────────────────────────

    @Test
    @DisplayName("상품 Top N — 요청 limit 만큼 페이지 크기를 지정해 조회")
    void getTopProducts_appliesLimit() {
        given(statsRepository.findTopProducts(eq(period.fromAt()), eq(period.toAtExclusive()),
                anyCollection(), eq(OrderItemStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(new ProductSalesResponse(1L, "상품A", 5_000L, 5L)));

        List<ProductSalesResponse> products = statsService.getTopProducts(period, 5);

        assertThat(products).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(statsRepository).should().findTopProducts(any(), any(), anyCollection(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("판매자 Top N — 요청 limit 만큼 페이지 크기를 지정해 조회")
    void getTopSellers_appliesLimit() {
        given(statsRepository.findTopSellers(eq(period.fromAt()), eq(period.toAtExclusive()),
                anyCollection(), eq(OrderItemStatus.ACTIVE), any(Pageable.class)))
                .willReturn(List.of(new SellerSalesResponse(7L, 9_000L, 3L, 2L)));

        List<SellerSalesResponse> sellers = statsService.getTopSellers(period, 50);

        assertThat(sellers).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(statsRepository).should().findTopSellers(any(), any(), anyCollection(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    // ── 실패주문 추이 ────────────────────────────────────────────

    @Test
    @DisplayName("실패주문 추이 — 데이터 없는 날짜는 0으로 채움")
    void getFailedOrderTrend_fillsEmptyDates() {
        given(statsRepository.aggregateDailyFailedOrders(period.fromAt(), period.toAtExclusive()))
                .willReturn(List.of(new DailyCountAggregate(FROM.plusDays(1), 2L)));

        List<FailedOrderTrendResponse> trend = statsService.getFailedOrderTrend(period);

        assertThat(trend).extracting(FailedOrderTrendResponse::date)
                .containsExactly(FROM, FROM.plusDays(1), TO);
        assertThat(trend).extracting(FailedOrderTrendResponse::count)
                .containsExactly(0L, 2L, 0L);
    }

    // ── helpers ─────────────────────────────────────────────────

    private void givenSales(long revenue, long orderCount) {
        given(statsRepository.aggregateSales(eq(period.fromAt()), eq(period.toAtExclusive()),
                anyCollection(), eq(OrderItemStatus.ACTIVE)))
                .willReturn(new SalesAggregate(revenue, orderCount));
    }

    private void givenCancels(long total, long fully, long partially) {
        given(statsRepository.aggregateOrderCancels(period.fromAt(), period.toAtExclusive(),
                OrderStatus.CANCELLED, OrderStatus.PARTIALLY_CANCELLED, OrderStatus.PENDING))
                .willReturn(new OrderCancelAggregate(total, fully, partially));
    }
}
