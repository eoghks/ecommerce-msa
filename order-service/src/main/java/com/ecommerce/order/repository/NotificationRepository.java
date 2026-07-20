package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 목록 — 최신순 */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 미읽음 개수 (뱃지용, 경량) */
    long countByUserIdAndIsReadFalse(Long userId);

    /** 전체 읽음 처리 — 본인 미읽음 알림만 일괄 갱신 */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true "
            + "where n.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId);
}
