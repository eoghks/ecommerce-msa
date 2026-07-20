package com.ecommerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 인앱 알림 (V1.1-4).
 * 주문·배송 상태 전이 시 주문 소유자(userId) 대상으로 생성된다.
 * title/message 는 NotificationType 의 상수 템플릿으로 조립 — 개인정보 미포함.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    // 관련 주문 (클릭 시 상세 이동) — 주문 외 알림 확장 대비 nullable
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(Long userId, NotificationType type, String title,
                         String message, Long orderId) {
        this.userId   = userId;
        this.type     = type;
        this.title    = title;
        this.message  = message;
        this.orderId  = orderId;
        this.isRead   = false;
    }

    /** 본인 소유 알림 여부 — 타인 알림 읽음 차단(404) 판정용 */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 읽음 처리 (멱등) */
    public void markRead() {
        this.isRead = true;
    }
}
