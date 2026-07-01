package com.ecommerce.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        return Order.builder()
                .userId(10L)
                .totalPrice(1_500_000L)
                .items(List.of(item1, item2))
                .build();
    }

    @Test
    @DisplayName("주문 생성 시 초기 상태는 PENDING")
    void createOrder_statusIsPending() {
        Order order = buildOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
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
    @DisplayName("PENDING 상태에서만 취소 가능")
    void isCancellable_onlyWhenPending() {
        Order order = buildOrder();
        assertThat(order.isCancellable()).isTrue();

        order.confirm();
        assertThat(order.isCancellable()).isFalse();
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
}
