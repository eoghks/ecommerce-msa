package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.request.OrderItemRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock private OrderRepository          orderRepository;
    @Mock private ProductClient            productClient;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    // ── 주문 생성 ──────────────────────────────────────────────────

    @Test
    @DisplayName("주문 생성 — 상품 정보 조회 후 저장 및 ApplicationEvent 발행")
    void createOrder_success() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 2))
        );
        ProductClient.ProductInfo productInfo =
                new ProductClient.ProductInfo(10L, "갤럭시 S24", 1_200_000L, 10);
        Order savedOrder = buildOrder(userId, 10L, "갤럭시 S24", 1_200_000L, 2);

        given(productClient.getProduct(10L)).willReturn(productInfo);
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderCreatedApplicationEvent.class));
    }

    // ── 주문 확정 ──────────────────────────────────────────────────

    @Test
    @DisplayName("confirmOrder — PENDING → CONFIRMED")
    void confirmOrder_success() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.confirmOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirmOrder — 이미 CONFIRMED 면 멱등 처리 (skip)")
    void confirmOrder_idempotent() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        order.confirm();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // 두 번 호출해도 예외 없음
        orderService.confirmOrder(1L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirmOrder — 존재하지 않는 주문은 예외 발생")
    void confirmOrder_notFound() {
        given(orderRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── Saga 보상 취소 ──────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder (Saga) — PENDING → CANCELLED")
    void cancelOrder_saga_success() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelOrder — 이미 CANCELLED 면 멱등 처리 (skip)")
    void cancelOrder_idempotent() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ── 주문 목록 조회 ──────────────────────────────────────────────

    @Test
    @DisplayName("getMyOrders — userId 기준 페이징 조회")
    void getMyOrders_success() {
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        given(orderRepository.findByUserId(userId, pageable)).willReturn(page);

        Page<OrderResponse> result = orderService.getMyOrders(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(userId);
    }

    // ── 주문 상세 조회 ──────────────────────────────────────────────

    @Test
    @DisplayName("getOrder — 본인 주문 조회 성공")
    void getOrder_success() {
        Long userId = 1L;
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(1L, userId);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getOrder — 타인 주문 조회 시 404")
    void getOrder_otherUser_notFound() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(1L, 999L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── 사용자 주문 취소 ────────────────────────────────────────────

    @Test
    @DisplayName("cancelByUser — PENDING 주문 취소 성공")
    void cancelByUser_success() {
        Long userId = 1L;
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelByUser(1L, userId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelByUser — CONFIRMED 주문 취소 시도 → 409")
    void cancelByUser_alreadyConfirmed() {
        Long userId = 1L;
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        order.confirm();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByUser(1L, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소할 수 없는 주문 상태");
    }

    @Test
    @DisplayName("cancelByUser — 타인 주문 취소 시도 → 404")
    void cancelByUser_otherUser() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByUser(1L, 999L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── helper ──────────────────────────────────────────────────────

    private Order buildOrder(Long userId, Long productId, String productName,
                              Long price, int quantity) {
        OrderItem item = OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .build();
        return Order.builder()
                .userId(userId)
                .totalPrice(price * quantity)
                .items(List.of(item))
                .build();
    }
}
