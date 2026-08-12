package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.DailyCountAggregate;
import com.ecommerce.order.dto.DailySalesAggregate;
import com.ecommerce.order.dto.OrderCancelAggregate;
import com.ecommerce.order.dto.SalesAggregate;
import com.ecommerce.order.dto.response.ProductSalesResponse;
import com.ecommerce.order.dto.response.SellerSalesResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 관리자 매출 통계 집계 조회 (V1.1-7).
 *
 * - 모든 지표는 DB 집계 쿼리 1회로 계산한다(애플리케이션 루프 집계 금지, 파라미터 바인딩 필수).
 * - 유효 매출은 주문의 total_price(생성 시점 금액, 부분취소 미반영)가 아니라
 *   ACTIVE 항목의 `price * quantity` 합계를 사용한다.
 * - 기간 조건은 항상 `createdAt >= fromAt and createdAt < toAtExclusive` (종료일 하루 전체 포함).
 * - 실패주문 추이는 FailedOrderLog 대상 집계지만, 통계 쿼리를 한곳에서 관리하기 위해 함께 둔다.
 * - CRUD 노출이 필요 없어 JpaRepository 대신 Repository 마커를 사용한다.
 */
public interface StatsRepository extends Repository<Order, Long> {

    /**
     * 유효 매출·주문수 집계.
     * ACTIVE 항목만 합산하므로 부분취소 주문의 취소 항목은 제외된다.
     * 전 항목이 취소된 주문은 상태가 CANCELLED 로 전이돼 집계 대상 상태에서 빠진다.
     */
    @Query("""
            select new com.ecommerce.order.dto.SalesAggregate(
                       coalesce(sum(i.price * i.quantity), 0L),
                       count(distinct o.id))
            from Order o
            join o.items i
            where o.createdAt >= :fromAt
              and o.createdAt < :toAtExclusive
              and o.status in :orderStatuses
              and i.status = :activeItemStatus
            """)
    SalesAggregate aggregateSales(@Param("fromAt") LocalDateTime fromAt,
                                  @Param("toAtExclusive") LocalDateTime toAtExclusive,
                                  @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
                                  @Param("activeItemStatus") OrderItemStatus activeItemStatus);

    /** 일별 유효 매출·주문수 집계 — 주문 생성일(created_at) 기준, 날짜 오름차순 */
    @Query("""
            select new com.ecommerce.order.dto.DailySalesAggregate(
                       cast(o.createdAt as LocalDate),
                       coalesce(sum(i.price * i.quantity), 0L),
                       count(distinct o.id))
            from Order o
            join o.items i
            where o.createdAt >= :fromAt
              and o.createdAt < :toAtExclusive
              and o.status in :orderStatuses
              and i.status = :activeItemStatus
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)
            """)
    List<DailySalesAggregate> aggregateDailySales(@Param("fromAt") LocalDateTime fromAt,
                                                 @Param("toAtExclusive") LocalDateTime toAtExclusive,
                                                 @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
                                                 @Param("activeItemStatus") OrderItemStatus activeItemStatus);

    /**
     * 취소 집계 — 전체(PENDING 제외)·전체취소·부분취소를 한 쿼리로 분리 집계한다.
     * PENDING 은 미확정 주문이라 취소율 분모에서 제외한다.
     */
    @Query("""
            select new com.ecommerce.order.dto.OrderCancelAggregate(
                       count(o),
                       coalesce(sum(case when o.status = :fullyCancelledStatus then 1L else 0L end), 0L),
                       coalesce(sum(case when o.status = :partiallyCancelledStatus then 1L else 0L end), 0L))
            from Order o
            where o.createdAt >= :fromAt
              and o.createdAt < :toAtExclusive
              and o.status <> :excludedStatus
            """)
    OrderCancelAggregate aggregateOrderCancels(@Param("fromAt") LocalDateTime fromAt,
                                               @Param("toAtExclusive") LocalDateTime toAtExclusive,
                                               @Param("fullyCancelledStatus") OrderStatus fullyCancelledStatus,
                                               @Param("partiallyCancelledStatus") OrderStatus partiallyCancelledStatus,
                                               @Param("excludedStatus") OrderStatus excludedStatus);

    /**
     * 상품별 매출 Top N — 매출 내림차순, 동일 매출은 productId 오름차순(정렬 안정성).
     * 상품명은 주문 시점 스냅샷이라 상품당 대표값 하나(max)를 사용한다.
     */
    @Query("""
            select new com.ecommerce.order.dto.response.ProductSalesResponse(
                       i.productId,
                       max(i.productName),
                       coalesce(sum(i.price * i.quantity), 0L),
                       coalesce(sum(i.quantity), 0L))
            from Order o
            join o.items i
            where o.createdAt >= :fromAt
              and o.createdAt < :toAtExclusive
              and o.status in :orderStatuses
              and i.status = :activeItemStatus
            group by i.productId
            order by sum(i.price * i.quantity) desc, i.productId asc
            """)
    List<ProductSalesResponse> findTopProducts(@Param("fromAt") LocalDateTime fromAt,
                                               @Param("toAtExclusive") LocalDateTime toAtExclusive,
                                               @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
                                               @Param("activeItemStatus") OrderItemStatus activeItemStatus,
                                               Pageable pageable);

    /** 판매자별 매출 Top N — sellerId 가 null 이면 플랫폼(ADMIN) 등록 상품 */
    @Query("""
            select new com.ecommerce.order.dto.response.SellerSalesResponse(
                       i.sellerId,
                       coalesce(sum(i.price * i.quantity), 0L),
                       coalesce(sum(i.quantity), 0L),
                       count(distinct o.id))
            from Order o
            join o.items i
            where o.createdAt >= :fromAt
              and o.createdAt < :toAtExclusive
              and o.status in :orderStatuses
              and i.status = :activeItemStatus
            group by i.sellerId
            order by sum(i.price * i.quantity) desc, i.sellerId asc
            """)
    List<SellerSalesResponse> findTopSellers(@Param("fromAt") LocalDateTime fromAt,
                                             @Param("toAtExclusive") LocalDateTime toAtExclusive,
                                             @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
                                             @Param("activeItemStatus") OrderItemStatus activeItemStatus,
                                             Pageable pageable);

    /** 기간 내 실패(자동취소) 주문 건수 */
    @Query("""
            select count(f)
            from FailedOrderLog f
            where f.occurredAt >= :fromAt
              and f.occurredAt < :toAtExclusive
            """)
    long countFailedOrders(@Param("fromAt") LocalDateTime fromAt,
                           @Param("toAtExclusive") LocalDateTime toAtExclusive);

    /** 실패(자동취소) 주문 일별 추이 — 발생일(occurred_at) 기준, 날짜 오름차순 */
    @Query("""
            select new com.ecommerce.order.dto.DailyCountAggregate(
                       cast(f.occurredAt as LocalDate),
                       count(f))
            from FailedOrderLog f
            where f.occurredAt >= :fromAt
              and f.occurredAt < :toAtExclusive
            group by cast(f.occurredAt as LocalDate)
            order by cast(f.occurredAt as LocalDate)
            """)
    List<DailyCountAggregate> aggregateDailyFailedOrders(@Param("fromAt") LocalDateTime fromAt,
                                                        @Param("toAtExclusive") LocalDateTime toAtExclusive);
}
