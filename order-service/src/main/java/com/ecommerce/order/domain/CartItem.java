package com.ecommerce.order.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 로그인 사용자 장바구니 — PostgreSQL 영구 저장.
 * 비로그인 장바구니는 Redis(cart:guest:{guestId})에 저장.
 */
@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // HR-04: 수량 하한 1 / 상한 999 검증
    private static final int MAX_QUANTITY = 999;

    public void updateQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1 ~ " + MAX_QUANTITY + " 사이여야 합니다.");
        }
        this.quantity = quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void addQuantity(int delta) {
        int next = this.quantity + delta;
        if (next < 1 || next > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1 ~ " + MAX_QUANTITY + " 사이여야 합니다.");
        }
        this.quantity = next;
        this.updatedAt = LocalDateTime.now();
    }
}
