package com.ecommerce.order.repository;

import com.ecommerce.order.config.JpaConfig;
import com.ecommerce.order.domain.FailedOrderLog;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.DailyCountAggregate;
import com.ecommerce.order.dto.DailySalesAggregate;
import com.ecommerce.order.dto.OrderCancelAggregate;
import com.ecommerce.order.dto.SalesAggregate;
import com.ecommerce.order.dto.response.ProductSalesResponse;
import com.ecommerce.order.dto.response.SellerSalesResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매출 통계 집계 쿼리 통합 테스트 (V1.1-7).
 * 유효 매출 = 집계 대상 주문(CONFIRMED/PARTIALLY_CANCELLED)의 ACTIVE 항목 합계임을 실제 DB로 검증한다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@DisplayName("StatsRepository 매출 통계 집계 통합 테스트 (V1.1-7)")
class StatsRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private StatsRepository     statsRepository;
    @Autowired private TestEntityManager   testEntityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 7, 3);

    /** 유효 매출 집계 대상 주문 상태 */
    private static final Set<OrderStatus> REVENUE_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    /** 집계는 단일 쿼리 1회로 수행돼야 한다 */
    private static final int SINGLE_QUERY = 1;

    // ── 유효 매출 ────────────────────────────────────────────────

    @Test
    @DisplayName("유효 매출은 ACTIVE 항목만 합산 — 부분취소 주문의 취소 항목은 제외")
    void aggregateSales_activeItemsOnly() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 2, 7L), item(2L, "상품B", 500L, 1, 7L)));
        // 마지막 항목(3_000원)이 취소되는 부분취소 주문 → 2_000원만 매출
        saveOrder(OrderStatus.PARTIALLY_CANCELLED, FROM.atStartOfDay().plusHours(5),
                List.of(item(3L, "상품C", 2_000L, 1, 8L), item(4L, "상품D", 3_000L, 1, 8L)));

        SalesAggregate sales = aggregateSales();

        assertThat(sales.revenue()).isEqualTo(4_500L);
        assertThat(sales.orderCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("PENDING(미확정)·CANCELLED(전체취소) 주문은 매출·주문수에서 제외")
    void aggregateSales_excludesPendingAndCancelled() {
        saveOrder(OrderStatus.PENDING, FROM.atStartOfDay(), List.of(item(1L, "상품A", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.CANCELLED, FROM.atStartOfDay(), List.of(item(2L, "상품B", 2_000L, 1, 7L)));

        SalesAggregate sales = aggregateSales();

        assertThat(sales.revenue()).isZero();
        assertThat(sales.orderCount()).isZero();
    }

    @Test
    @DisplayName("기간 경계 — 시작일 00:00 과 종료일 23:59 는 포함, 기간 밖은 제외")
    void aggregateSales_periodBoundary() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.CONFIRMED, TO.atTime(23, 59, 59),
                List.of(item(2L, "상품B", 2_000L, 1, 7L)));
        saveOrder(OrderStatus.CONFIRMED, FROM.minusDays(1).atTime(23, 59, 59),
                List.of(item(3L, "상품C", 4_000L, 1, 7L)));
        saveOrder(OrderStatus.CONFIRMED, TO.plusDays(1).atStartOfDay(),
                List.of(item(4L, "상품D", 8_000L, 1, 7L)));

        SalesAggregate sales = aggregateSales();

        assertThat(sales.revenue()).isEqualTo(3_000L);
        assertThat(sales.orderCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("데이터가 없으면 0 반환 (null 금지)")
    void aggregateSales_noData_returnsZero() {
        SalesAggregate sales = aggregateSales();

        assertThat(sales.revenue()).isZero();
        assertThat(sales.orderCount()).isZero();
    }

    @Test
    @DisplayName("매출 집계는 단일 쿼리로 수행된다 (데이터량에 비례하지 않음)")
    void aggregateSales_singleQuery() {
        for (int i = 0; i < 5; i++) {
            saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay().plusHours(i),
                    List.of(item(1L + i, "상품" + i, 1_000L, 1, 7L)));
        }
        Statistics statistics = statistics();
        statistics.clear();

        aggregateSales();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(SINGLE_QUERY);
    }

    // ── 일별 추이 ────────────────────────────────────────────────

    @Test
    @DisplayName("일별 집계 — 날짜별로 그룹핑되고 오름차순 정렬, 데이터 없는 날은 결과에 없다")
    void aggregateDailySales_groupsByDate() {
        saveOrder(OrderStatus.CONFIRMED, TO.atTime(10, 0), List.of(item(1L, "상품A", 3_000L, 1, 7L)));
        saveOrder(OrderStatus.CONFIRMED, FROM.atTime(9, 0), List.of(item(2L, "상품B", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.CONFIRMED, FROM.atTime(20, 0), List.of(item(3L, "상품C", 500L, 2, 7L)));

        List<DailySalesAggregate> daily = statsRepository.aggregateDailySales(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                REVENUE_STATUSES, OrderItemStatus.ACTIVE);

        assertThat(daily).extracting(DailySalesAggregate::date).containsExactly(FROM, TO);
        assertThat(daily).extracting(DailySalesAggregate::revenue).containsExactly(2_000L, 3_000L);
        assertThat(daily).extracting(DailySalesAggregate::orderCount).containsExactly(2L, 1L);
    }

    // ── 취소 집계 ────────────────────────────────────────────────

    @Test
    @DisplayName("취소 집계 — 전체취소/부분취소를 분리하고 PENDING 은 분모에서 제외")
    void aggregateOrderCancels_separatesCounts() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(), List.of(item(1L, "상품A", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.CANCELLED, FROM.atStartOfDay(), List.of(item(2L, "상품B", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.PARTIALLY_CANCELLED, FROM.atStartOfDay(),
                List.of(item(3L, "상품C", 1_000L, 1, 7L), item(4L, "상품D", 1_000L, 1, 7L)));
        saveOrder(OrderStatus.PENDING, FROM.atStartOfDay(), List.of(item(5L, "상품E", 1_000L, 1, 7L)));

        OrderCancelAggregate cancels = statsRepository.aggregateOrderCancels(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                OrderStatus.CANCELLED, OrderStatus.PARTIALLY_CANCELLED, OrderStatus.PENDING);

        assertThat(cancels.totalCount()).isEqualTo(3L);
        assertThat(cancels.fullyCancelledCount()).isEqualTo(1L);
        assertThat(cancels.partiallyCancelledCount()).isEqualTo(1L);
    }

    // ── Top N ───────────────────────────────────────────────────

    @Test
    @DisplayName("상품 Top N — 매출 내림차순, limit 적용, 취소 항목 제외")
    void findTopProducts_orderAndLimit() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 3, 7L), item(2L, "상품B", 5_000L, 1, 7L)));
        // 상품C(9_000원)는 취소 항목이므로 집계에서 빠진다
        saveOrder(OrderStatus.PARTIALLY_CANCELLED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 1, 7L), item(3L, "상품C", 9_000L, 1, 7L)));

        List<ProductSalesResponse> top = statsRepository.findTopProducts(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                REVENUE_STATUSES, OrderItemStatus.ACTIVE, PageRequest.ofSize(2));

        assertThat(top).extracting(ProductSalesResponse::productId).containsExactly(2L, 1L);
        assertThat(top).extracting(ProductSalesResponse::revenue).containsExactly(5_000L, 4_000L);
        assertThat(top.get(1).quantity()).isEqualTo(4L);
        assertThat(top).extracting(ProductSalesResponse::productId).doesNotContain(3L);
    }

    @Test
    @DisplayName("상품 Top N — limit 개수만 반환")
    void findTopProducts_appliesLimit() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 1, 7L),
                        item(2L, "상품B", 2_000L, 1, 7L),
                        item(3L, "상품C", 3_000L, 1, 7L)));

        List<ProductSalesResponse> top = statsRepository.findTopProducts(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                REVENUE_STATUSES, OrderItemStatus.ACTIVE, PageRequest.ofSize(1));

        assertThat(top).hasSize(1);
        assertThat(top.get(0).productId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("판매자 Top N — 판매자별 매출 합계, 매출 내림차순")
    void findTopSellers_groupsBySeller() {
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(1L, "상품A", 1_000L, 2, 7L), item(2L, "상품B", 5_000L, 1, 8L)));
        saveOrder(OrderStatus.CONFIRMED, FROM.atStartOfDay(),
                List.of(item(3L, "상품C", 1_000L, 1, 7L)));

        List<SellerSalesResponse> top = statsRepository.findTopSellers(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                REVENUE_STATUSES, OrderItemStatus.ACTIVE, PageRequest.ofSize(10));

        assertThat(top).extracting(SellerSalesResponse::sellerId).containsExactly(8L, 7L);
        assertThat(top).extracting(SellerSalesResponse::revenue).containsExactly(5_000L, 3_000L);
        assertThat(top.get(1).orderCount()).isEqualTo(2L);
    }

    // ── 실패주문 ────────────────────────────────────────────────

    @Test
    @DisplayName("실패주문 — 기간 내 건수와 일별 추이 집계")
    void failedOrders_countAndDailyTrend() {
        saveFailedLog(FROM.atTime(9, 0));
        saveFailedLog(FROM.atTime(18, 0));
        saveFailedLog(TO.atTime(1, 0));
        saveFailedLog(TO.plusDays(1).atStartOfDay());   // 기간 밖

        long count = statsRepository.countFailedOrders(FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay());
        List<DailyCountAggregate> trend = statsRepository.aggregateDailyFailedOrders(
                FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay());

        assertThat(count).isEqualTo(3L);
        assertThat(trend).extracting(DailyCountAggregate::date).containsExactly(FROM, TO);
        assertThat(trend).extracting(DailyCountAggregate::count).containsExactly(2L, 1L);
    }

    // ── helpers ─────────────────────────────────────────────────

    private SalesAggregate aggregateSales() {
        return statsRepository.aggregateSales(FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay(),
                REVENUE_STATUSES, OrderItemStatus.ACTIVE);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private Order saveOrder(OrderStatus status, LocalDateTime createdAt, List<OrderItem> items) {
        Order order = Order.builder()
                .userId(1L)
                .totalPrice(items.stream().mapToLong(OrderItem::subtotal).sum())
                .receiver("홍길동")
                .phone("010-1234-5678")
                .address("서울시 강남구")
                .items(items)
                .build();
        testEntityManager.persist(order);
        testEntityManager.flush();
        applyStatus(order, status);
        testEntityManager.flush();
        overrideCreatedAt(order.getId(), createdAt);
        testEntityManager.clear();
        return order;
    }

    /** 주문 상태를 도메인 전이 메서드로 세팅 (V1.1-6: 생성 직후는 PAYMENT_PENDING) */
    private void applyStatus(Order order, OrderStatus status) {
        switch (status) {
            case CONFIRMED -> {
                order.markPaid();
                order.confirm();
            }
            case PARTIALLY_CANCELLED -> {
                order.markPaid();
                order.confirm();
                List<OrderItem> items = order.getItems();
                order.cancelItem(items.get(items.size() - 1).getId(), "테스트 항목 취소");
            }
            case CANCELLED -> order.cancel();
            case PENDING -> order.markPaid();
            case PAYMENT_PENDING -> { /* 생성 직후 상태 유지 */ }
        }
    }

    /** created_at 은 감사(@CreatedDate) 컬럼이라 저장 후 네이티브 업데이트로 조정한다 */
    private void overrideCreatedAt(Long orderId, LocalDateTime createdAt) {
        testEntityManager.getEntityManager()
                .createNativeQuery("update orders set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", orderId)
                .executeUpdate();
    }

    private void saveFailedLog(LocalDateTime occurredAt) {
        FailedOrderLog failedOrderLog = FailedOrderLog.builder()
                .orderId(1L)
                .userId(1L)
                .reason("재고 확보 실패(자동취소)")
                .build();
        testEntityManager.persist(failedOrderLog);
        testEntityManager.flush();
        testEntityManager.getEntityManager()
                .createNativeQuery("update failed_order_log set occurred_at = :occurredAt where id = :id")
                .setParameter("occurredAt", occurredAt)
                .setParameter("id", failedOrderLog.getId())
                .executeUpdate();
        testEntityManager.clear();
    }

    private OrderItem item(Long productId, String productName, Long price, int quantity, Long sellerId) {
        return OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .sellerId(sellerId)
                .build();
    }
}
