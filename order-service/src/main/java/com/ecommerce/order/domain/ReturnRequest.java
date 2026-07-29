package com.ecommerce.order.domain;

import com.ecommerce.order.exception.InvalidReturnStatusException;
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
 * 반품 요청 (V1.1-5).
 * 배송 완료(DELIVERED) 주문의 활성 항목에 대해 주문 소유자가 신청하고,
 * 판매자/관리자가 승인(재고 복구 + 환불) 또는 거부한다.
 * 항목 단위 전량 반품만 지원 — 부분 수량 반품은 백로그.
 */
@Entity
@Table(name = "return_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    // 신청자(주문 소유자) — auth-service users.id 참조
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    // 승인/거부 처리 시각
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private ReturnRequest(Long orderId, Long orderItemId, Long userId, String reason) {
        this.orderId     = orderId;
        this.orderItemId = orderItemId;
        this.userId      = userId;
        this.reason      = reason;
        this.status      = ReturnStatus.REQUESTED;
    }

    /** 신청자 본인 여부 — 타인 반품 조회 차단 판정용 */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 승인/거부 가능한(접수) 상태인지 */
    public boolean isRequested() {
        return this.status == ReturnStatus.REQUESTED;
    }

    /** 승인 — REQUESTED 에서만 가능. 그 외 전이는 400 */
    public void approve(LocalDateTime when) {
        requireRequested("승인");
        this.status      = ReturnStatus.APPROVED;
        this.processedAt = when;
    }

    /** 거부 — REQUESTED 에서만 가능. 사유 필수(서비스에서 검증) */
    public void reject(String rejectReason, LocalDateTime when) {
        requireRequested("거부");
        this.status       = ReturnStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.processedAt  = when;
    }

    /** 환불 완료 — APPROVED 에서만 가능 */
    public void markRefunded(LocalDateTime when) {
        if (this.status != ReturnStatus.APPROVED) {
            throw new InvalidReturnStatusException(
                    "환불 처리할 수 없는 반품 상태입니다. 현재 상태: " + this.status);
        }
        this.status      = ReturnStatus.REFUNDED;
        this.processedAt = when;
    }

    private void requireRequested(String action) {
        if (!isRequested()) {
            throw new InvalidReturnStatusException(
                    action + "할 수 없는 반품 상태입니다. 현재 상태: " + this.status);
        }
    }
}
