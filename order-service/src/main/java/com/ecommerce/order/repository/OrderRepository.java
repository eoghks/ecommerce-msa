package com.ecommerce.order.repository;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.AutoConfirmTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** 특정 판매자의 상품이 포함된 주문 (중복 제거) — 판매자 주문 관리용 */
    @Query("""
            select distinct o from Order o
            join o.items i
            where i.sellerId = :sellerId
            """)
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    /**
     * V1.1-1: 구매 인증 — 해당 사용자가 특정 상품을 취소되지 않은(ACTIVE) 항목으로 보유하고,
     * 실제 재고가 차감된(CONFIRMED/PARTIALLY_CANCELLED) 주문이 있는지.
     * PENDING(미차감)·CANCELLED(전체취소)는 실구매가 아니므로 제외해 리뷰 위조를 차단한다.
     * 리뷰 작성 자격 판정용 (product-service 내부 호출).
     */
    @Query("""
            select case when count(i) > 0 then true else false end
            from Order o
            join o.items i
            where o.userId = :userId
              and i.productId = :productId
              and i.status = :activeStatus
              and o.status in :purchasedStatuses
            """)
    boolean existsPurchasedProduct(@Param("userId") Long userId,
                                   @Param("productId") Long productId,
                                   @Param("activeStatus") OrderItemStatus activeStatus,
                                   @Param("purchasedStatuses") Collection<OrderStatus> purchasedStatuses);

    /**
     * 자동 구매확정 대상 조회 (§3.2) — 배송완료 후 기준일이 지난 미확정 주문.
     * 알림에 필요한 최소 컬럼만 조회하며, 대량 적체 대비 Pageable 로 배치 크기를 제한한다.
     * 오래 대기한 주문부터 처리해 특정 주문이 계속 밀리지 않게 한다.
     */
    @Query("""
            select new com.ecommerce.order.dto.AutoConfirmTarget(o.id, o.userId)
            from Order o
            where o.purchaseConfirmedAt is null
              and o.deliveryStatus = :deliveredStatus
              and o.deliveredAt is not null
              and o.deliveredAt <= :threshold
            order by o.deliveredAt asc
            """)
    List<AutoConfirmTarget> findAutoConfirmTargets(@Param("deliveredStatus") DeliveryStatus deliveredStatus,
                                                   @Param("threshold") LocalDateTime threshold,
                                                   Pageable pageable);

    /**
     * 자동 구매확정 원자 처리 (§3.2) — 다중 인스턴스 중복 확정 방지.
     * 조건부 UPDATE 이므로 여러 인스턴스가 동시에 같은 주문을 처리해도 갱신에 성공하는 쪽은 1개뿐이고,
     * 갱신 건수(1)를 받은 인스턴스만 알림을 발송한다(락·분산 스케줄러 불필요).
     * 벌크 연산은 감사(@LastModifiedDate)를 우회하므로 updatedAt 도 함께 갱신한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
            set o.purchaseConfirmedAt = :confirmedAt, o.updatedAt = :confirmedAt
            where o.id = :orderId
              and o.purchaseConfirmedAt is null
              and o.deliveryStatus = :deliveredStatus
              and o.deliveredAt is not null
              and o.deliveredAt <= :threshold
            """)
    int confirmPurchaseIfEligible(@Param("orderId") Long orderId,
                                  @Param("deliveredStatus") DeliveryStatus deliveredStatus,
                                  @Param("threshold") LocalDateTime threshold,
                                  @Param("confirmedAt") LocalDateTime confirmedAt);
}
