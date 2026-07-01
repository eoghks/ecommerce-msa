package com.ecommerce.order.repository;

import com.ecommerce.order.domain.FailedOrderLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedOrderLogRepository extends JpaRepository<FailedOrderLog, Long> {

    /** 최근 발생 순 실패 주문 로그 (ADMIN 조회용) */
    Page<FailedOrderLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
