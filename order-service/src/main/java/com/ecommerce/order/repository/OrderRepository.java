package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

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
}
