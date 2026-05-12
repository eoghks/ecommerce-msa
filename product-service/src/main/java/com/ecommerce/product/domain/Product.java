package com.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Product(String name, String description, Long price,
                    int stock, String imageUrl, Category category) {
        this.name        = name;
        this.description = description;
        this.price       = price;
        this.stock       = stock;
        this.imageUrl    = imageUrl;
        this.category    = category;
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
