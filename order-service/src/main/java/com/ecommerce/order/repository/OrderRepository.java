package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** 특정 판매자의 상품이 포함된 주문 (중복 제거) — 판매자 주문 관리용 */
    @Query("""
            select distinct o from Order o
            join o.items i
            where i.sellerId = :sellerId
            """)
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);
}
