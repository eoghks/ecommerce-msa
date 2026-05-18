package com.ecommerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Product Service 상품 ID — 서비스 간 DB 분리로 FK 제약 없이 저장
    @Column(nullable = false)
    private Long productId;

    // 주문 시점 상품명 스냅샷 — 이후 상품 정보 변경에 영향받지 않음
    @Column(nullable = false, length = 200)
    private String productName;

    // 주문 시점 단가 스냅샷
    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer quantity;

    @Builder
    private OrderItem(Long productId, String productName, Long price, Integer quantity) {
        this.productId   = productId;
        this.productName = productName;
        this.price       = price;
        this.quantity    = quantity;
    }

    // Order 엔티티에서만 호출 — 양방향 연관관계 설정
    void assignOrder(Order order) {
        this.order = order;
    }

    public long subtotal() {
        return price * quantity;
    }
}
