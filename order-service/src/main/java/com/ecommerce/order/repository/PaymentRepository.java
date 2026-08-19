package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 주문의 유효한 결제 1건 — 실패(FAILED) 건은 제외한다.
     * 주문당 승인 1건 보장(부분 유니크 인덱스)과 동일한 기준이라 항상 0~1건이다.
     */
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.status <> :failedStatus
            """)
    Optional<Payment> findActiveByOrderId(@Param("orderId") Long orderId,
                                          @Param("failedStatus") PaymentStatus failedStatus);

    /** 승인 멱등 판정 — 이미 승인·취소된 결제가 있으면 재승인을 막는다 (§8) */
    @Query("""
            select case when count(p) > 0 then true else false end
            from Payment p
            where p.orderId = :orderId
              and p.status in :statuses
            """)
    boolean existsByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                       @Param("statuses") Collection<PaymentStatus> statuses);

    /** PG 거래키 멱등 판정 — 웹훅·재전송으로 같은 거래가 두 번 승인되지 않게 한다 (§8) */
    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);
}
