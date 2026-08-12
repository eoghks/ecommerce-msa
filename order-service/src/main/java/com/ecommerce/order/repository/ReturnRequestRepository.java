package com.ecommerce.order.repository;

import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /** 동일 항목에 진행 중(활성)인 반품이 있는지 — 중복 신청 차단(409) 판정용 */
    boolean existsByOrderItemIdAndStatusIn(Long orderItemId, Collection<ReturnStatus> statuses);

    /** 내 반품 목록 — 최신순 */
    Page<ReturnRequest> findByUserIdOrderByRequestedAtDesc(Long userId, Pageable pageable);

    /** 전체 반품 목록 (ADMIN) — 최신순 */
    Page<ReturnRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    /**
     * 판매자 반품 목록 (SELLER) — 본인 상품 항목이 반품 대상인 건만, 최신순.
     * M-3: 주문 단위로 넓히면 멀티 셀러 주문에서 타 판매자 항목의 반품 정보가 노출되므로
     *      반품 대상 항목(order_item_id)의 판매자 기준으로 좁힌다.
     */
    @Query("""
            select r from ReturnRequest r
            where r.orderItemId in (
                select i.id from OrderItem i where i.sellerId = :sellerId
            )
            order by r.requestedAt desc
            """)
    Page<ReturnRequest> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);
}
