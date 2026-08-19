package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 주문의 유효한 결제 1건 — 실패(FAILED) 건은 제외한다.
     * 주문당 승인 1건 보장(부분 유니크 인덱스)과 동일한 기준이라 항상 0~1건이다.
     * C-01: 미확정(UNKNOWN) 결제도 포함된다 — 출금됐을 수 있는 건이 조회·환불 경로에서 사라지면 안 된다.
     */
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.status <> :failedStatus
            """)
    Optional<Payment> findActiveByOrderId(@Param("orderId") Long orderId,
                                          @Param("failedStatus") PaymentStatus failedStatus);

    /**
     * H-02: 취소·환불용 결제 조회 — 행을 잠그고 읽는다.
     * 같은 주문에 동시 부분환불(반품 승인 + 항목취소 등)이 들어오면 두 트랜잭션이 같은 cancelledAmount 를
     * 읽고 각자 덮어써(lost update) 잔여 취소가능액이 부풀어 초과환불이 가능해진다.
     * 비관적 락으로 직렬화해 두 번째 요청이 갱신된 누적 취소액을 보고 상한을 계산하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.status <> :failedStatus
            """)
    Optional<Payment> findActiveByOrderIdForUpdate(@Param("orderId") Long orderId,
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

    /**
     * M-03: 주문의 진행 중·미확정 결제 — 이중 승인(이중 출금)을 애플리케이션 단계에서 차단하는 데 쓴다.
     */
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.status in :statuses
            """)
    List<Payment> findByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                           @Param("statuses") Collection<PaymentStatus> statuses);

    /**
     * C-02: 본인 주문에 오래 남은 진행 중·미확정 결제 — 재결제 차단 해소(회수) 대상.
     * 고아 READY 는 부분 유니크 인덱스(uq_payment_order_active)를 점유해 재결제를 영구히 막으므로
     * 재조회로 승인 여부를 확정해야 한다. 소유자까지 함께 걸러 타인 주문 조회를 막는다.
     */
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.userId = :userId
              and p.status in :statuses
              and p.createdAt <= :threshold
            """)
    List<Payment> findStaleByOrderId(@Param("orderId") Long orderId,
                                     @Param("userId") Long userId,
                                     @Param("statuses") Collection<PaymentStatus> statuses,
                                     @Param("threshold") LocalDateTime threshold);

    /**
     * C-01/C-02: 오래 남은 진행 중·미확정 결제 일괄 회수 대상 (스케줄러).
     * 프로세스 종료·재시작으로 확정 경로를 놓친 결제를 재조회로 정리한다.
     * 대량 적체 시 배치가 길어지지 않게 Pageable 로 상한을 둔다.
     */
    @Query("""
            select p from Payment p
            where p.status in :statuses
              and p.createdAt <= :threshold
            order by p.createdAt asc
            """)
    List<Payment> findStalePayments(@Param("statuses") Collection<PaymentStatus> statuses,
                                    @Param("threshold") LocalDateTime threshold,
                                    Pageable pageable);
}
