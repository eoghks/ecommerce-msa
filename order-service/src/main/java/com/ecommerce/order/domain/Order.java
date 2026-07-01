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
import java.util.Optional;

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

    /**
     * M-N3: 사용자가 취소 가능한 주문인지.
     * PENDING(차감 전) + CONFIRMED/PARTIALLY_CANCELLED(차감 후) 모두 사용자 취소 허용.
     * 이미 전체 취소된(CANCELLED) 주문만 불가.
     */
    public boolean isUserCancellable() {
        return this.status == OrderStatus.PENDING
                || this.status == OrderStatus.CONFIRMED
                || this.status == OrderStatus.PARTIALLY_CANCELLED;
    }

    /**
     * M-N3: 사용자 주문 취소 — 활성(ACTIVE) 항목 전체를 사유와 함께 항목취소한다.
     * 차감된 항목만 재고 복구 이벤트가 나가도록, 실제 ACTIVE→CANCELLED 전이가 일어난 항목만 반환.
     *   - PENDING(미차감): isItemCancellable=false 이므로 항목취소 없이 단순 CANCELLED 전이 → 복구 이벤트 없음
     *   - CONFIRMED/부분취소(차감 후): 활성 항목만 전이 → 각 항목 복구 이벤트 발행 대상
     * @return 새로 취소된 항목 목록 (복구 이벤트 발행 대상)
     */
    public List<OrderItem> cancelByUser(String reason) {
        if (!isItemCancellable()) {
            // PENDING 등 미차감 주문은 재고 복구 없이 단순 취소
            cancel();
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<OrderItem> newlyCancelled = items.stream()
                .filter(OrderItem::isActive)
                .filter(item -> item.cancel(reason, now))
                .toList();
        recalculateAfterCancel();
        return newlyCancelled;
    }

    /**
     * C-2: 항목 단위 취소 가능 상태인지.
     * 재고가 실제로 차감된 주문(CONFIRMED) 또는 일부만 취소된 주문(PARTIALLY_CANCELLED)만 허용.
     * PENDING(아직 차감 전)·CANCELLED(차감된 적 없거나 이미 전체 취소)는 불가 →
     * 차감되지 않은 수량이 재고에 복구되는 과복구(over-restock)를 막는다.
     */
    public boolean isItemCancellable() {
        return this.status == OrderStatus.CONFIRMED
                || this.status == OrderStatus.PARTIALLY_CANCELLED;
    }

    /**
     * 항목 단위 취소 (판매자/관리자). 사유 필수.
     * 취소 후 주문 상태·합계를 재계산한다:
     *   - 전 항목 취소 → CANCELLED
     *   - 일부만 취소 → PARTIALLY_CANCELLED
     *   - totalPrice → 살아있는(ACTIVE) 항목 합계로 갱신
     * C-3: 실제로 ACTIVE→CANCELLED 전이가 일어난 경우에만 항목을 반환한다.
     *      이미 취소된 항목이면 Optional.empty() → 호출부가 재고 복구 이벤트를 중복 발행하지 않음.
     * @return 새로 취소된 항목 (없으면 empty)
     */
    public Optional<OrderItem> cancelItem(Long itemId, String reason) {
        OrderItem target = items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "주문에 해당 항목이 없습니다. itemId=" + itemId));

        boolean transitioned = target.cancel(reason, LocalDateTime.now());
        if (!transitioned) {
            return Optional.empty();   // 이미 취소된 항목 — 멱등, 이벤트 미발행
        }
        recalculateAfterCancel();
        return Optional.of(target);
    }

    private void recalculateAfterCancel() {
        boolean allCancelled = items.stream().noneMatch(OrderItem::isActive);
        boolean anyCancelled = items.stream().anyMatch(i -> !i.isActive());

        if (allCancelled) {
            this.status = OrderStatus.CANCELLED;
        } else if (anyCancelled) {
            this.status = OrderStatus.PARTIALLY_CANCELLED;
        }
        // 합계는 살아있는 항목만 반영
        this.totalPrice = items.stream()
                .filter(OrderItem::isActive)
                .mapToLong(OrderItem::subtotal)
                .sum();
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }
}
