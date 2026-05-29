package com.ecommerce.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")   // order는 SQL 예약어
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // auth-service users.id 참조 — 서비스 간 DB 분리로 FK 제약 없이 저장
    @Column(nullable = false)
    private Long userId;

    // OrderStatusConverter autoApply=true 로 자동 변환
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Long totalPrice;

    // HR-05: 배송 정보 — 주문 시 수령인·연락처·주소 저장
    @Column(length = 100)
    private String receiver;

    @Column(length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Order(Long userId, Long totalPrice, String receiver, String phone,
                  String address, List<OrderItem> items) {
        this.userId     = userId;
        this.totalPrice = totalPrice;
        this.receiver   = receiver;
        this.phone      = phone;
        this.address    = address;
        this.status     = OrderStatus.PENDING;
        if (items != null) {
            items.forEach(this::addItem);
        }
    }

    /**
     * 주문 확정 — 재고 차감 완료 이벤트 수신 시 호출.
     * 멱등 처리: 이미 CONFIRMED 이면 skip (Kafka at-least-once 재전달 대응)
     */
    public void confirm() {
        if (this.status == OrderStatus.CONFIRMED) {
            return;
        }
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "확정할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * 주문 취소 — 재고 부족 또는 사용자 요청 시 호출.
     * 멱등 처리: 이미 CANCELLED 이면 skip
     */
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return;
        }
        if (this.status == OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "이미 확정된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isCancellable() {
        return this.status == OrderStatus.PENDING;
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }
}
