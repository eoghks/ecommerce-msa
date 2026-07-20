package com.ecommerce.order.service;

import com.ecommerce.order.domain.Notification;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.dto.response.NotificationResponse;
import com.ecommerce.order.exception.NotificationNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 서비스 (V1.1-4).
 * - 생성: 주문·배송 상태 전이 지점(OrderService)에서 호출 — 상태 전이 트랜잭션에 합류.
 * - 조회/읽음: 전 API 본인(userId) 소유만 접근. userId 부재 → 401, 타인 알림 → 404.
 * - title/message 는 NotificationType 상수 템플릿으로 조립 (개인정보 미포함).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** 알림 생성 — 상태 전이 트랜잭션 내에서 호출(단순 insert) */
    @Transactional
    public void create(Long userId, NotificationType type, Long orderId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(type.getTitle())
                .message(type.formatMessage(orderId))
                .orderId(orderId)
                .build();
        notificationRepository.save(notification);
    }

    /** 내 알림 목록 (최신순 페이징) */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        requireUser(userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    /** 미읽음 개수 (뱃지용) */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        requireUser(userId);
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /** 단건 읽음 (본인 소유만) */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = findOwned(userId, notificationId);
        notification.markRead();
    }

    /** 전체 읽음 (본인 미읽음 알림 일괄) */
    @Transactional
    public void markAllRead(Long userId) {
        requireUser(userId);
        notificationRepository.markAllRead(userId);
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }

    /** 본인 소유 알림 조회 — 없거나 타인 소유면 404 (정보 노출 방지) */
    private Notification findOwned(Long userId, Long notificationId) {
        requireUser(userId);
        return notificationRepository.findById(notificationId)
                .filter(n -> n.isOwnedBy(userId))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
