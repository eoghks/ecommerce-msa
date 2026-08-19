package com.ecommerce.order.repository;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.ReturnStatus;
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
     *
     * H-1: 확정 대상 주문상태(CONFIRMED/PARTIALLY_CANCELLED)만 조회한다 — 전체 취소·전체 반품된
     *      주문도 배송상태는 DELIVERED 로 남아 상태를 보지 않으면 취소 주문까지 확정된다.
     * H-2: 진행 중 반품이 걸린 주문은 제외한다 — 판매자가 처리를 미뤄 자동확정이 먼저 일어나면
     *      사용자의 반품 자격이 사라진다. 반품이 종결되면 다음 실행에서 다시 대상이 된다.
     */
    @Query("""
            select new com.ecommerce.order.dto.AutoConfirmTarget(o.id, o.userId)
            from Order o
            where o.purchaseConfirmedAt is null
              and o.deliveryStatus = :deliveredStatus
              and o.status in :confirmableStatuses
              and o.deliveredAt is not null
              and o.deliveredAt <= :threshold
              and not exists (
                    select r.id from ReturnRequest r
                    where r.orderId = o.id
                      and r.status in :pendingReturnStatuses
              )
            order by o.deliveredAt asc
            """)
    List<AutoConfirmTarget> findAutoConfirmTargets(@Param("deliveredStatus") DeliveryStatus deliveredStatus,
                                                   @Param("confirmableStatuses") Collection<OrderStatus> confirmableStatuses,
                                                   @Param("pendingReturnStatuses") Collection<ReturnStatus> pendingReturnStatuses,
                                                   @Param("threshold") LocalDateTime threshold,
                                                   Pageable pageable);

    /**
     * 자동 구매확정 원자 처리 (§3.2) — 다중 인스턴스 중복 확정 방지.
     * 조건부 UPDATE 이므로 여러 인스턴스가 동시에 같은 주문을 처리해도 갱신에 성공하는 쪽은 1개뿐이고,
     * 갱신 건수(1)를 받은 인스턴스만 알림을 발송한다(락·분산 스케줄러 불필요).
     * 벌크 연산은 감사(@LastModifiedDate)를 우회하므로 updatedAt 도 함께 갱신한다.
     * 조회(findAutoConfirmTargets)와 동일한 자격 조건을 그대로 반복해야 원자성이 유지된다(H-1·H-2).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
            set o.purchaseConfirmedAt = :confirmedAt, o.updatedAt = :confirmedAt
            where o.id = :orderId
              and o.purchaseConfirmedAt is null
              and o.deliveryStatus = :deliveredStatus
              and o.status in :confirmableStatuses
              and o.deliveredAt is not null
              and o.deliveredAt <= :threshold
              and not exists (
                    select r.id from ReturnRequest r
                    where r.orderId = o.id
                      and r.status in :pendingReturnStatuses
              )
            """)
    int confirmPurchaseIfEligible(@Param("orderId") Long orderId,
                                  @Param("deliveredStatus") DeliveryStatus deliveredStatus,
                                  @Param("confirmableStatuses") Collection<OrderStatus> confirmableStatuses,
                                  @Param("pendingReturnStatuses") Collection<ReturnStatus> pendingReturnStatuses,
                                  @Param("threshold") LocalDateTime threshold,
                                  @Param("confirmedAt") LocalDateTime confirmedAt);

    /**
     * 수동 구매확정 원자 처리 (§3.2, H-3) — 자동확정과 동일한 조건부 UPDATE 경로.
     * 읽기-수정-쓰기(dirty checking) 대신 조건부 UPDATE 를 써서 동시 요청·자동확정과의 경합에서도
     * 갱신에 성공한 1건만 확정·알림이 되게 한다(갱신 0건 → 이미 확정 → 409).
     * 기준일(threshold) 조건이 없다는 점만 자동확정과 다르다 — 사용자는 배송완료 즉시 확정할 수 있다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
            set o.purchaseConfirmedAt = :confirmedAt, o.updatedAt = :confirmedAt
            where o.id = :orderId
              and o.purchaseConfirmedAt is null
              and o.deliveryStatus = :deliveredStatus
              and o.status in :confirmableStatuses
              and not exists (
                    select r.id from ReturnRequest r
                    where r.orderId = o.id
                      and r.status in :pendingReturnStatuses
              )
            """)
    int confirmPurchaseNow(@Param("orderId") Long orderId,
                           @Param("deliveredStatus") DeliveryStatus deliveredStatus,
                           @Param("confirmableStatuses") Collection<OrderStatus> confirmableStatuses,
                           @Param("pendingReturnStatuses") Collection<ReturnStatus> pendingReturnStatuses,
                           @Param("confirmedAt") LocalDateTime confirmedAt);

    /**
     * V1.1-6: 미완료 주문 만료 (payment-foundation §11.1) — 결제 대기로 방치된 주문을 일괄 취소한다.
     * 조건부 벌크 UPDATE 라 다중 인스턴스가 동시에 실행해도 같은 주문이 두 번 취소되지 않는다.
     * 승인 전 단계라 재고 복구·환불 대상이 없어 건별 후처리가 필요 없다.
     * 벌크 연산은 감사(@LastModifiedDate)를 우회하므로 updatedAt 도 함께 갱신한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
            set o.status = :cancelledStatus, o.updatedAt = :now
            where o.status = :paymentPendingStatus
              and o.createdAt <= :threshold
            """)
    int expirePaymentPendingOrders(@Param("paymentPendingStatus") OrderStatus paymentPendingStatus,
                                   @Param("cancelledStatus") OrderStatus cancelledStatus,
                                   @Param("threshold") LocalDateTime threshold,
                                   @Param("now") LocalDateTime now);
}
