package com.ecommerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * 저장형 배송지 주소록. 사용자당 기본 배송지는 최대 1개(서비스에서 유일성 보장).
 * 주문 시엔 값을 스냅샷으로 복사해 저장하므로, 이후 주소록 수정/삭제와 무관하게 주문 배송지는 보존된다.
 */
@Entity
@Table(name = "address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // auth-service users.id 참조 — 서비스 간 DB 분리로 FK 제약 없이 저장
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String receiver;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Address(Long userId, String receiver, String phone, String address, boolean isDefault) {
        this.userId    = userId;
        this.receiver  = receiver;
        this.phone     = phone;
        this.address   = address;
        this.isDefault = isDefault;
    }

    /** 본인 소유 여부 — IDOR 방지 판정용 */
    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }

    /** 배송지 내용 수정 */
    public void update(String receiver, String phone, String address) {
        this.receiver = receiver;
        this.phone    = phone;
        this.address  = address;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}
