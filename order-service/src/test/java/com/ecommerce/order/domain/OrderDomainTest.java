package com.ecommerce.order.domain;

import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 도메인 단위 테스트")
class OrderDomainTest {

    private Order buildOrder() {
        OrderItem item1 = OrderItem.builder()
                .productId(1L)
                .productName("갤럭시 S24")
                .price(1_200_000L)
                .quantity(1)
                .build();

        OrderItem item2 = OrderItem.builder()
                .productId(2L)
                .productName("나이키 운동화")
                .price(150_000L)
                .quantity(2)
                .build();

        Order order = Order.builder()
                .userId(10L)
                .totalPrice(1_500_000L)
                .items(List.of(item1, item2))
                .build();
        // V1.1-6: 주문은 PAYMENT_PENDING 으로 생성되므로 결제 승인 후 상태(PENDING)를 기본 전제로 둔다
        order.markPaid();
        return order;
    }

    /** 결제 승인 전(PAYMENT_PENDING) 주문 — 선생성 상태 검증용 */
    private Order unpaidOrder() {
        OrderItem item = OrderItem.builder()
                .productId(1L)
                .productName("갤럭시 S24")
                .price(1_200_000L)
                .quantity(1)
                .build();
        return Order.builder()
                .userId(10L)
                .totalPrice(1_200_000L)
                .items(List.of(item))
                .build();
    }

    @Test
    @DisplayName("V1.1-6: 주문 생성 시 초기 상태는 PAYMENT_PENDING (승인 전 재고 미차감)")
    void createOrder_statusIsPaymentPending() {
        Order order = unpaidOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.isAwaitingPayment()).isTrue();
    }

    @Test
    @DisplayName("V1.1-6: markPaid — PAYMENT_PENDING → PENDING, 이미 PENDING 이면 멱등 skip")
    void markPaid_transitionsToPending() {
        Order order = unpaidOrder();

        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        order.markPaid();   // 멱등 — 예외 없이 유지
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("V1.1-6: markPaid — 결제 단계가 아닌 주문(CONFIRMED)은 전이 불가")
    void markPaid_invalidStatus_throws() {
        Order order = buildOrder();
        order.confirm();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 완료 처리할 수 없는");
    }

    @Test
    @DisplayName("주문 생성 시 OrderItem 양방향 연관관계 설정")
    void createOrder_itemsLinked() {
        Order order = buildOrder();
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems()).allMatch(item -> item.getOrder() == order);
    }

    @Test
    @DisplayName("confirm() 호출 시 상태가 CONFIRMED로 변경")
    void confirm_changesStatusToConfirmed() {
        Order order = buildOrder();
        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("cancel() 호출 시 상태가 CANCELLED로 변경")
    void cancel_changesStatusToCancelled() {
        Order order = buildOrder();
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("재고 차감 전(PAYMENT_PENDING/PENDING)에만 취소 가능")
    void isCancellable_beforeStockDecrease() {
        assertThat(unpaidOrder().isCancellable()).isTrue();

        Order order = buildOrder();
        assertThat(order.isCancellable()).isTrue();

        order.confirm();
        assertThat(order.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("M-N3: isUserCancellable — PENDING/CONFIRMED/PARTIALLY_CANCELLED 가능, CANCELLED 불가")
    void isUserCancellable_states() {
        assertThat(unpaidOrder().isUserCancellable()).isTrue();   // PAYMENT_PENDING

        Order pending = buildOrder();
        assertThat(pending.isUserCancellable()).isTrue();   // PENDING

        Order confirmed = buildOrder();
        confirmed.confirm();
        assertThat(confirmed.isUserCancellable()).isTrue();  // CONFIRMED

        Order cancelled = buildOrder();
        cancelled.cancel();
        assertThat(cancelled.isUserCancellable()).isFalse(); // CANCELLED
    }

    @Test
    @DisplayName("M-N3: cancelByUser — PENDING(미차감)은 복구 대상 없이 CANCELLED")
    void cancelByUser_pending_noRestockTargets() {
        Order order = buildOrder();

        List<OrderItem> targets = order.cancelByUser("고객 주문 취소");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("M-N3: cancelByUser — CONFIRMED(차감)은 활성 항목 전체를 복구 대상으로 반환하고 CANCELLED")
    void cancelByUser_confirmed_returnsActiveItems() {
        Order order = buildOrder();
        order.confirm();

        List<OrderItem> targets = order.cancelByUser("고객 주문 취소");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(targets).hasSize(2);   // 활성 항목 2개 모두 복구 대상
        assertThat(order.getTotalPrice()).isEqualTo(0L);
    }

    @Test
    @DisplayName("OrderItem subtotal() — 단가 × 수량 계산")
    void orderItem_subtotal() {
        OrderItem item = OrderItem.builder()
                .productId(1L)
                .productName("갤럭시 S24")
                .price(1_200_000L)
                .quantity(2)
                .build();

        assertThat(item.subtotal()).isEqualTo(2_400_000L);
    }

    @Test
    @DisplayName("OrderStatus.fromCode() — 유효한 코드 변환")
    void orderStatus_fromCode() {
        assertThat(OrderStatus.fromCode("PENDING")).isEqualTo(OrderStatus.PENDING);
        assertThat(OrderStatus.fromCode("CONFIRMED")).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.fromCode("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("OrderStatus.fromCode() — 잘못된 코드는 예외 발생")
    void orderStatus_fromCode_invalid() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OrderStatus.fromCode("UNKNOWN")
        );
    }

    // ── 금액 모델 (payment-foundation §1) ──────────────────────────

    @Test
    @DisplayName("금액모델 — 주문 생성 시 itemsTotal = payableAmount = 항목 합계, 할인·적립은 0")
    void createOrder_amountsInitialized() {
        Order order = buildOrder();

        assertThat(order.getItemsTotal()).isEqualTo(1_500_000L);
        assertThat(order.getPayableAmount()).isEqualTo(1_500_000L);
        assertThat(order.getTotalPrice()).isEqualTo(1_500_000L);
        assertThat(order.getCouponDiscount()).isZero();
        assertThat(order.getMileageUsed()).isZero();
        assertThat(order.getMileageEarned()).isZero();
    }

    @Test
    @DisplayName("금액모델 — 항목 취소 시 ACTIVE 항목 기준으로 itemsTotal·payableAmount 재계산")
    void cancelItem_recalculatesAmounts() {
        Order order = identifiedOrder();
        order.confirm();

        order.cancelItem(2L, "판매자 취소");   // 나이키 운동화 300,000 취소

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELLED);
        assertThat(order.getItemsTotal()).isEqualTo(1_200_000L);
        assertThat(order.getPayableAmount()).isEqualTo(1_200_000L);
        assertThat(order.getTotalPrice()).isEqualTo(order.getItemsTotal());
    }

    @Test
    @DisplayName("금액모델 — 불변식 payableAmount = itemsTotal - couponDiscount - mileageUsed 유지")
    void recalculate_keepsPayableInvariant() {
        Order order = identifiedOrder();
        order.confirm();
        // 쿠폰·마일리지 도입 전이라 setter 가 없으므로 할인이 있는 상태를 모사한다
        ReflectionTestUtils.setField(order, "couponDiscount", 50_000L);
        ReflectionTestUtils.setField(order, "mileageUsed", 30_000L);

        order.cancelItem(2L, "판매자 취소");

        assertThat(order.getPayableAmount())
                .isEqualTo(order.getItemsTotal() - order.getCouponDiscount() - order.getMileageUsed());
        assertThat(order.getPayableAmount()).isEqualTo(1_120_000L);
    }

    @Test
    @DisplayName("금액모델 — 재계산 결과 payableAmount 가 음수면 예외 (payableAmount >= 0)")
    void recalculate_negativePayable_throws() {
        Order order = identifiedOrder();
        order.confirm();
        ReflectionTestUtils.setField(order, "couponDiscount", 1_400_000L);

        // 1,200,000 항목만 남아 할인액(1,400,000)에 못 미치므로 불변식 위반
        assertThatThrownBy(() -> order.cancelItem(2L, "판매자 취소"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0원 미만");
    }

    // ── 금액 모델 (payment-foundation §1) ─────────────────────────

    @Test
    @DisplayName("생성(M-3) — 합계는 항목에서 계산되며 금액 모델이 함께 채워진다")
    void createOrder_amountsFromItems() {
        Order order = buildOrder();

        assertThat(order.getItemsTotal()).isEqualTo(1_500_000L);
        assertThat(order.getTotalPrice()).isEqualTo(1_500_000L);
        assertThat(order.getPayableAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("생성(M-3) — 전달된 합계가 항목 합계와 다르면 생성 자체를 막는다")
    void createOrder_totalPriceMismatch_throws() {
        OrderItem item = OrderItem.builder()
                .productId(1L).productName("갤럭시 S24").price(1_200_000L).quantity(1)
                .build();

        assertThatThrownBy(() -> Order.builder()
                .userId(10L)
                .totalPrice(999L)          // 항목 합계(1,200,000)와 불일치
                .items(List.of(item))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("항목 합계");
    }

    // ── 구매확정 (payment-foundation §3.2) ────────────────────────

    @Test
    @DisplayName("구매확정 자격 — 배송완료 + 미확정 주문은 통과한다")
    void validateConfirmable_delivered_passes() {
        Order order = deliveredOrder();

        order.validateConfirmable();

        assertThat(order.isConfirmable()).isTrue();
        assertThat(order.isPurchaseConfirmed()).isFalse();
    }

    @Test
    @DisplayName("구매확정 자격 — 배송완료 전(준비중·배송중)이면 400")
    void validateConfirmable_notDelivered_throws() {
        Order preparing = buildOrder();
        preparing.confirm();
        assertThatThrownBy(preparing::validateConfirmable)
                .isInstanceOf(PurchaseConfirmNotAllowedException.class);

        Order shipping = buildOrder();
        shipping.confirm();
        shipping.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        assertThatThrownBy(shipping::validateConfirmable)
                .isInstanceOf(PurchaseConfirmNotAllowedException.class);
    }

    @Test
    @DisplayName("구매확정 자격 — 이미 확정된 주문 재확정은 409")
    void validateConfirmable_alreadyConfirmed_throws() {
        Order order = deliveredOrder();
        ReflectionTestUtils.setField(order, "purchaseConfirmedAt", LocalDateTime.now());

        assertThatThrownBy(order::validateConfirmable)
                .isInstanceOf(PurchaseAlreadyConfirmedException.class);
    }

    @Test
    @DisplayName("구매확정 자격(H-1) — 전 항목 취소(CANCELLED)된 주문은 배송완료여도 400")
    void validateConfirmable_cancelledOrder_throws() {
        Order order = deliveredOrder();
        order.cancelItem(1L, "반품 승인");
        order.cancelItem(2L, "반품 승인");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.isConfirmable()).isFalse();
        assertThatThrownBy(order::validateConfirmable)
                .isInstanceOf(PurchaseConfirmNotAllowedException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("구매확정 자격(H-1) — 일부만 취소된(PARTIALLY_CANCELLED) 주문은 확정 가능")
    void validateConfirmable_partiallyCancelledOrder_passes() {
        Order order = deliveredOrder();
        order.cancelItem(2L, "반품 승인");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELLED);
        order.validateConfirmable();
        assertThat(order.isConfirmable()).isTrue();
    }

    @Test
    @DisplayName("구매확정 — DELIVERED 전이 시 deliveredAt 이 기록된다(자동확정 기준 시각)")
    void advanceDeliveryStatus_recordsDeliveredAt() {
        Order order = buildOrder();
        order.confirm();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        assertThat(order.getDeliveredAt()).isNull();

        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);

        assertThat(order.getDeliveredAt()).isNotNull();
    }

    /** 항목 id 가 부여된(영속 상태 모사) 주문 — 항목 취소 테스트용 */
    private Order identifiedOrder() {
        List<OrderItem> items = new ArrayList<>();
        items.add(itemOf(1L, 1L, "갤럭시 S24", 1_200_000L, 1));
        items.add(itemOf(2L, 2L, "나이키 운동화", 150_000L, 2));

        Order order = Order.builder().userId(10L).totalPrice(1_500_000L).items(items).build();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.markPaid();   // V1.1-6: 결제 승인 완료 상태(PENDING)를 기본 전제로 둔다
        return order;
    }

    /** 배송완료 주문 — 구매확정 자격 충족 상태 */
    private Order deliveredOrder() {
        Order order = identifiedOrder();
        order.confirm();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        return order;
    }

    private OrderItem itemOf(Long id, Long productId, String name, Long price, int quantity) {
        OrderItem item = OrderItem.builder()
                .productId(productId).productName(name).price(price).quantity(quantity)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
