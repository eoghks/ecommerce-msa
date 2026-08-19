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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결제 (payment-foundation §5, §7).
 * 주문 1건당 승인 1건만 존재한다(부분 유니크 인덱스 uq_payment_order_active).
 * 승인 금액은 주문의 payableAmount 스냅샷이며, 취소·환불은 cancelledAmount 로 누적한다(부분취소 §4.2).
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    /** 실패 사유 저장 상한 — DB 컬럼(varchar 500)과 동일 기준 */
    private static final int MAX_FAIL_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 결제자 — 주문 소유자와 동일. 조회 시 본인 확인에 사용한다 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 20)
    private PaymentProvider pgProvider;

    /** PG 거래키 — 취소·조회에 사용. 0원 결제(PG 미사용)는 null */
    @Column(name = "pg_payment_key", length = 200)
    private String pgPaymentKey;

    @Column(nullable = false)
    private Long amount;

    /** 누적 취소·환불 금액 — 부분 반품이 여러 번 일어나도 승인 금액을 넘지 않는다 (§4.2) */
    @Column(name = "cancelled_amount", nullable = false)
    private Long cancelledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "fail_reason", length = MAX_FAIL_REASON_LENGTH)
    private String failReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Payment(Long orderId, Long userId, PaymentProvider pgProvider, Long amount) {
        this.orderId         = orderId;
        this.userId          = userId;
        this.pgProvider      = pgProvider;
        this.amount          = amount;
        this.cancelledAmount = 0L;
        this.status          = PaymentStatus.READY;
    }

    /** 승인 완료 — PG 응답의 거래키·승인시각을 스냅샷한다. 0원 결제는 거래키가 없다(§11-3) */
    public void approve(String pgPaymentKey, LocalDateTime approvedAt) {
        this.pgPaymentKey = pgPaymentKey;
        this.approvedAt   = approvedAt;
        this.status       = PaymentStatus.APPROVED;
    }

    /** 승인 실패 — 사유는 저장 상한까지만 남긴다(카드정보 등 원문 노출 방지는 호출부 책임) */
    public void fail(String reason) {
        this.status     = PaymentStatus.FAILED;
        this.failReason = truncate(reason);
    }

    /**
     * 취소·환불 반영 (§4.2).
     * 누적 취소액이 승인 금액에 도달하면 CANCELED 로 전이하고, 부분취소면 APPROVED 를 유지한다.
     */
    public void applyCancel(long cancelAmount, LocalDateTime canceledAt) {
        this.cancelledAmount += cancelAmount;
        this.canceledAt       = canceledAt;
        if (this.cancelledAmount >= this.amount) {
            this.status = PaymentStatus.CANCELED;
        }
    }

    public boolean isApproved() {
        return this.status == PaymentStatus.APPROVED;
    }

    /** 취소 가능한 잔액 — 이미 취소된 금액을 뺀 나머지. 멱등 재취소를 0으로 막는다 */
    public long cancellableAmount() {
        return this.amount - this.cancelledAmount;
    }

    /** PG 왕복이 필요한 결제인지 — 0원 결제(거래키 없음)는 내부 처리만 한다 */
    public boolean requiresPgCall() {
        return this.pgPaymentKey != null && !this.pgPaymentKey.isBlank();
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_FAIL_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_FAIL_REASON_LENGTH);
    }
}
