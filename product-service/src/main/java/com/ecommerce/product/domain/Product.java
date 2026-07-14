package com.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(of = "id")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 원화 가격 — Long (원화는 소수점 없음) */
    @Column(nullable = false)
    private Long price;

    /** 재고 음수 불가 — 차감 전 검증 필수 */
    @Column(nullable = false)
    private int stock;

    @Column(length = 500)
    private String imageUrl;

    /** 판매자 ID — null이면 ADMIN이 등록한 상품 */
    @Column(name = "seller_id")
    private Long sellerId;

    /** 판매 상태 — 기본 ACTIVE, ADMIN이 BANNED로 전환 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 평균 별점 (0.0~5.0) — 리뷰 변경 시 집계 재계산 UPDATE로 갱신 (V1.1-1) */
    @Column(name = "rating_avg", nullable = false, precision = 2, scale = 1)
    private BigDecimal ratingAvg;

    /** 리뷰 개수 — 리뷰 변경 시 집계 재계산 UPDATE로 갱신 (V1.1-1) */
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Product(String name, String description, Long price,
                    int stock, String imageUrl, Long sellerId, Category category) {
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.stock       = stock;
        this.imageUrl    = imageUrl;
        this.sellerId    = sellerId;
        this.category    = category;
        this.status      = ProductStatus.ACTIVE;
        this.ratingAvg   = BigDecimal.ZERO.setScale(1);
        this.ratingCount = 0;
    }

    /** SELLER 본인 상품인지 확인 */
    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.sellerId);
    }

    /** 판매 금지 (ADMIN) */
    public void ban() {
        this.status = ProductStatus.BANNED;
    }

    /** 판매 금지 해제 (ADMIN) */
    public void unban() {
        this.status = ProductStatus.ACTIVE;
    }

    public boolean isBanned() {
        return this.status == ProductStatus.BANNED;
    }

    /** 이미지 URL 교체 (마이그레이션용) */
    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** 상품 정보 수정 */
    public void update(String name, String description, Long price,
                       int stock, String imageUrl, Category category) {
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.stock       = stock;
        this.imageUrl    = imageUrl;
        this.category    = category;
    }

    /** 재고 차감 — 음수 방지 검증 후 호출 */
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + this.stock);
        }
        this.stock -= quantity;
    }

    /** 재고 복구 (주문 취소 시) */
    public void increaseStock(int quantity) {
        this.stock += quantity;
    }
}
