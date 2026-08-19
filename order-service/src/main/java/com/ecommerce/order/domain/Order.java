package com.ecommerce.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.ecommerce.order.exception.InvalidDeliveryStatusException;
import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
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

    /**
     * F-04: 주문 목록 조회 N+1 방지용 항목 배치 조회 크기.
     * 페이징 최대 크기(size)보다 크게 잡아 한 페이지의 항목을 한 번에 로드한다.
     */
    private static final int ITEMS_BATCH_SIZE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // auth-service users.id 참조 — 서비스 간 DB 분리로 FK 제약 없이 저장
    @Column(nullable = false)
    private Long userId;

    // OrderStatusConverter autoApply=true 로 자동 변환
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * ACTIVE 항목의 정가 합계. 결제·할인 도입 전부터 쓰던 값으로 itemsTotal 과 항상 같은 값을 가진다.
     * 실제 결제금액은 payableAmount 이며, 화면·정산에서 결제금액이 필요하면 totalPrice 가 아닌
     * payableAmount 를 사용한다(payment-foundation §1).
     */
    @Column(nullable = false)
    private Long totalPrice;

    // ── 금액 모델 (payment-foundation §1) ──────────────────────
    // 불변식: payableAmount = itemsTotal - couponDiscount - mileageUsed, payableAmount >= 0

    /** 항목 정가 합계(ACTIVE 항목) — totalPrice 와 동일 기준 */
    @Column(name = "items_total", nullable = false)
    private Long itemsTotal;

    /** 쿠폰 할인액 — 쿠폰 미도입(V1.1-10) 이므로 현재는 항상 0 */
    @Column(name = "coupon_discount", nullable = false)
    private Long couponDiscount;

    /** 마일리지 사용액 — 마일리지 미도입(V1.1-9) 이므로 현재는 항상 0 */
    @Column(name = "mileage_used", nullable = false)
    private Long mileageUsed;

    /** 실 결제금액 = itemsTotal - couponDiscount - mileageUsed */
    @Column(name = "payable_amount", nullable = false)
    private Long payableAmount;

    /** 적립 마일리지 — 구매확정 시점에 적립(V1.1-9). 현재는 항상 0 */
    @Column(name = "mileage_earned", nullable = false)
    private Long mileageEarned;

    // HR-05: 배송 정보 — 주문 시 수령인·연락처·주소 저장
    @Column(length = 100)
    private String receiver;

    @Column(length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    // V1.1-3: 배송 진행 상태 — 준비중→배송중→배송완료. OrderStatus와 별도 축
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    /** 배송완료(DELIVERED) 전이 시각 — 자동 구매확정 기준일 계산에 사용 (§3.2) */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 구매확정 시각. null 이면 미확정(= 반품 가능 구간) (§3.2) */
    @Column(name = "purchase_confirmed_at")
    private LocalDateTime purchaseConfirmedAt;

    // F-04: 목록 조회 시 주문별 개별 조회(N+1) 대신 항목을 배치로 한 번에 로드
    @BatchSize(size = ITEMS_BATCH_SIZE)
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
        this.receiver   = receiver;
        this.phone      = phone;
        this.address    = address;
        // §5: 주문을 결제 대기로 선생성한 뒤 승인되면 PENDING 으로 전이한다.
        //     ("승인은 됐는데 주문 생성 실패" 차단 — 모든 결제 시도가 주문으로 추적된다)
        this.status     = OrderStatus.PAYMENT_PENDING;
        this.deliveryStatus = DeliveryStatus.PREPARING;
        // 할인 수단(쿠폰·마일리지) 미도입 — 항목 합계가 그대로 결제금액이 된다 (§1)
        this.couponDiscount = 0L;
        this.mileageUsed    = 0L;
        this.mileageEarned  = 0L;
        if (items != null) {
            items.forEach(this::addItem);
        }
        applyAmounts(resolveItemsTotal(totalPrice));
    }

    /**
     * M-3: 금액의 단일 진실원천은 주문 항목이다.
     * 합계는 항목에서 직접 계산하고, 전달된 totalPrice 는 대조용으로만 쓴다.
     * 값이 다르면 잘못된 합계가 금액 모델(itemsTotal/payableAmount)까지 오염시키기 전에 막는다.
     */
    private long resolveItemsTotal(Long requestedTotal) {
        long calculated = items.stream().mapToLong(OrderItem::subtotal).sum();
        if (requestedTotal != null && requestedTotal != calculated) {
            throw new IllegalArgumentException(
                    "주문 합계가 항목 합계와 일치하지 않습니다. 전달=" + requestedTotal
                            + ", 항목합계=" + calculated);
        }
        return calculated;
    }

    /** 결제 승인 대기 상태인지 — 재고 차감 전이라 만료·재결제 대상이 된다 (§5) */
    public boolean isAwaitingPayment() {
        return this.status == OrderStatus.PAYMENT_PENDING;
    }

    /**
     * 결제 승인 완료 — PAYMENT_PENDING → PENDING (§5).
     * 이 전이 이후에야 재고 차감 Saga(order.created)가 시작된다.
     * 멱등 처리: 이미 PENDING 이면 skip (승인 재요청·웹훅 재전달 대응)
     */
    public void markPaid() {
        if (this.status == OrderStatus.PENDING) {
            return;
        }
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "결제 완료 처리할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.PENDING;
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

    /** 재고 차감 전(결제 대기·승인 완료) 주문인지 — 차감 없이 바로 취소할 수 있다 */
    public boolean isCancellable() {
        return this.status == OrderStatus.PAYMENT_PENDING
                || this.status == OrderStatus.PENDING;
    }

    /**
     * M-N3: 사용자가 취소 가능한 주문인지.
     * PAYMENT_PENDING/PENDING(차감 전) + CONFIRMED/PARTIALLY_CANCELLED(차감 후) 모두 사용자 취소 허용.
     * 이미 전체 취소된(CANCELLED) 주문만 불가.
     */
    public boolean isUserCancellable() {
        return this.status == OrderStatus.PAYMENT_PENDING
                || this.status == OrderStatus.PENDING
                || this.status == OrderStatus.CONFIRMED
                || this.status == OrderStatus.PARTIALLY_CANCELLED;
    }

    /**
     * 이미 전체 취소된 주문인지.
     * 사용자 재취소 요청을 멱등(no-op)으로 처리할지 판정하는 데 사용한다.
     */
    public boolean isFullyCancelled() {
        return this.status == OrderStatus.CANCELLED;
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

    /**
     * V1.1-3: 배송상태 변경 대상 주문인지.
     * 배송상태는 재고가 차감된(CONFIRMED/PARTIALLY_CANCELLED) 주문에서만 의미가 있다.
     * PENDING(미확정)·CANCELLED(취소)는 진행 대상이 아니다.
     */
    public boolean isDeliverable() {
        return this.status == OrderStatus.CONFIRMED
                || this.status == OrderStatus.PARTIALLY_CANCELLED;
    }

    /** 주문 항목 중 해당 판매자의 상품이 있는지 — 판매자 배송상태 변경 권한 판정용 */
    public boolean hasSellerItem(Long sellerId) {
        return items.stream().anyMatch(item -> item.isOwnedBy(sellerId));
    }

    /**
     * V1.1-3: 배송상태 전진. PREPARING→SHIPPING→DELIVERED 순서만 허용.
     * 대상 아닌 주문 상태·역행/건너뜀/동일 상태 재설정은 InvalidDeliveryStatusException(400).
     */
    public void advanceDeliveryStatus(DeliveryStatus next) {
        if (!isDeliverable()) {
            throw new InvalidDeliveryStatusException(
                    "배송상태를 변경할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        if (!this.deliveryStatus.canAdvanceTo(next)) {
            throw new InvalidDeliveryStatusException(
                    "잘못된 배송상태 전이입니다: " + this.deliveryStatus + " → " + next);
        }
        this.deliveryStatus = next;
        // 자동 구매확정 기준일(배송완료 + N일) 계산을 위해 전이 시각을 기록한다 (§3.2)
        if (next == DeliveryStatus.DELIVERED) {
            this.deliveredAt = LocalDateTime.now();
        }
    }

    /** 구매확정 여부 — 확정된 주문은 반품 자격이 없다 (§3.2) */
    public boolean isPurchaseConfirmed() {
        return this.purchaseConfirmedAt != null;
    }

    /**
     * 구매확정 자격 검증 (§3.2). 자격: 확정 대상 주문상태(CONFIRMED/PARTIALLY_CANCELLED)
     * + 배송완료(DELIVERED) + 미확정.
     * 자격 미충족이면 400(PurchaseConfirmNotAllowedException),
     * 이미 확정됐으면 409(PurchaseAlreadyConfirmedException).
     *
     * H-1: 전체 취소·전체 반품된 주문은 deliveryStatus 가 DELIVERED 로 남으므로 주문상태를 함께 본다.
     * H-3: 실제 확정 기록은 조건부 UPDATE(OrderRepository)가 담당한다 — 동시 요청에서 1건만 성공하도록
     *      읽기-수정-쓰기 대신 원자적 UPDATE 를 쓰며, 이 메서드는 사용자에게 돌려줄 사유 판정만 한다.
     */
    public void validateConfirmable() {
        if (!isConfirmable()) {
            throw new PurchaseConfirmNotAllowedException(
                    "구매확정할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        if (this.deliveryStatus != DeliveryStatus.DELIVERED) {
            throw new PurchaseConfirmNotAllowedException(
                    "배송 완료된 주문만 구매확정할 수 있습니다. 현재 배송상태: " + this.deliveryStatus);
        }
        if (isPurchaseConfirmed()) {
            throw new PurchaseAlreadyConfirmedException(this.id);
        }
    }

    /** H-1: 구매확정 대상 주문 상태인지 — 취소(CANCELLED)·미차감(PENDING) 주문은 확정 불가 */
    public boolean isConfirmable() {
        return OrderStatus.confirmableStatuses().contains(this.status);
    }

    private void recalculateAfterCancel() {
        boolean allCancelled = items.stream().noneMatch(OrderItem::isActive);
        boolean anyCancelled = items.stream().anyMatch(i -> !i.isActive());

        if (allCancelled) {
            this.status = OrderStatus.CANCELLED;
        } else if (anyCancelled) {
            this.status = OrderStatus.PARTIALLY_CANCELLED;
        }
        // 합계는 살아있는 항목만 반영 — 금액 모델(itemsTotal/payableAmount)도 함께 갱신
        applyAmounts(items.stream()
                .filter(OrderItem::isActive)
                .mapToLong(OrderItem::subtotal)
                .sum());
    }

    /**
     * 금액 모델 일괄 갱신 — 불변식 유지 지점 (§1).
     * payableAmount = itemsTotal - couponDiscount - mileageUsed 이며 음수가 될 수 없다.
     * totalPrice 는 itemsTotal 과 같은 값(정가 합계)으로 유지한다.
     */
    private void applyAmounts(long newItemsTotal) {
        long payable = newItemsTotal - this.couponDiscount - this.mileageUsed;
        if (payable < 0) {
            throw new IllegalStateException(
                    "결제금액은 0원 미만이 될 수 없습니다. itemsTotal=" + newItemsTotal
                            + ", couponDiscount=" + this.couponDiscount
                            + ", mileageUsed=" + this.mileageUsed);
        }
        this.itemsTotal    = newItemsTotal;
        this.totalPrice    = newItemsTotal;
        this.payableAmount = payable;
    }

    private void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }
}
