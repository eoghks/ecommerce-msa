package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.request.OrderItemRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderItemAccessDeniedException;
import com.ecommerce.order.exception.OrderItemNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock private OrderRepository           orderRepository;
    @Mock private ProductClient             productClient;
    @Mock private OrderPersistenceService   orderPersistenceService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    // ── 주문 생성 ──────────────────────────────────────────────────

    @Test
    @DisplayName("주문 생성 — 상품 조회 후 영속 빈(saveAndPublish)에 위임 (프록시 경유 트랜잭션 보장)")
    void createOrder_success() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 2)),
                "홍길동", "010-1234-5678", "서울시 강남구 테헤란로 1"
        );
        ProductClient.ProductInfo productInfo =
                new ProductClient.ProductInfo(10L, "갤럭시 S24", 1_200_000L, 10, "https://example.com/img.jpg", 7L);
        Order savedOrder = buildOrder(userId, 10L, "갤럭시 S24", 1_200_000L, 2);
        OrderResponse expected = OrderResponse.from(savedOrder);

        given(productClient.getProduct(10L)).willReturn(productInfo);
        // 가격은 서버(ProductClient)에서 조회한 값으로 계산: 1,200,000 × 2
        given(orderPersistenceService.saveAndPublish(eq(userId), eq(2_400_000L), anyList(), eq(request)))
                .willReturn(expected);

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // 자기호출 제거 검증: 별도 빈에 위임됐는지
        then(orderPersistenceService).should(times(1))
                .saveAndPublish(eq(userId), eq(2_400_000L), anyList(), eq(request));
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

    @Test
    @DisplayName("getAllOrders — 전체 주문 페이징 조회 (ADMIN)")
    void getAllOrders_success() {
        PageRequest pageable = PageRequest.of(0, 20);
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        given(orderRepository.findAll(pageable)).willReturn(page);

        Page<OrderResponse> result = orderService.getAllOrders(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSellerOrders — 본인 상품 항목만 노출, 합계도 본인 항목 기준")
    void getSellerOrders_onlyOwnItems() {
        Long sellerId = 7L;
        PageRequest pageable = PageRequest.of(0, 20);
        // 한 주문에 판매자 7(2만원×1)과 판매자 8(5만원×1) 상품이 섞임
        Order order = buildMultiSellerOrder(
                new long[]{7L, 8L},
                new long[]{20_000L, 50_000L});
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        given(orderRepository.findBySellerId(sellerId, pageable)).willReturn(page);

        Page<OrderResponse> result = orderService.getSellerOrders(sellerId, pageable);

        OrderResponse res = result.getContent().get(0);
        // 판매자 7 항목만 보여야 함
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).sellerId()).isEqualTo(7L);
        // 합계도 판매자 7 항목(2만원)만
        assertThat(res.totalPrice()).isEqualTo(20_000L);
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

    // ── 주문 항목 취소 (부분 취소) ──────────────────────────────────

    @Test
    @DisplayName("cancelOrderItem — 일부 항목 취소 시 PARTIALLY_CANCELLED + 합계 재계산")
    void cancelOrderItem_partial() {
        // 판매자 7(2만), 판매자 8(5만) 섞인 주문. itemId=1이 판매자 7
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 1L, "재고 소진", 7L, "SELLER");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELLED);
        // 살아있는 항목(판매자 8, 5만)만 합계
        assertThat(order.getTotalPrice()).isEqualTo(50_000L);
        // 재고 복구 이벤트(AFTER_COMMIT 발행용) 등록 확인
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(com.ecommerce.order.event.OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("cancelOrderItem — 전 항목 취소 시 CANCELLED")
    void cancelOrderItem_all() {
        Order order = buildMultiSellerOrder(new long[]{7L, 7L}, new long[]{20_000L, 30_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 1L, "사유1", 7L, "SELLER");
        orderService.cancelOrderItem(1L, 2L, "사유2", 7L, "SELLER");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getTotalPrice()).isEqualTo(0L);
    }

    @Test
    @DisplayName("cancelOrderItem — SELLER가 타 판매자 항목 취소 시 403")
    void cancelOrderItem_seller_otherItem() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // 판매자 7이 itemId=2(판매자 8 항목) 취소 시도
        assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 2L, "사유", 7L, "SELLER"))
                .isInstanceOf(OrderItemAccessDeniedException.class);
    }

    @Test
    @DisplayName("cancelOrderItem — ADMIN은 어떤 항목이든 취소 가능")
    void cancelOrderItem_admin_anyItem() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 2L, "관리자 조치", 999L, "ADMIN");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELLED);
        assertThat(order.getTotalPrice()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("cancelOrderItem — 존재하지 않는 항목 취소 시 404")
    void cancelOrderItem_itemNotFound() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 999L, "사유", 7L, "SELLER"))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    @DisplayName("C-2: PENDING(미차감) 주문 항목 취소 시 409 — 과복구 방지")
    void cancelOrderItem_pending_rejected() {
        // confirm 안 한 PENDING 주문 (재고 차감 전)
        Order order = Order.builder().userId(1L).totalPrice(20_000L)
                .items(List.of(OrderItem.builder()
                        .productId(100L).productName("상품").price(20_000L).quantity(1).sellerId(7L).build()))
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order.getItems().get(0), "id", 1L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrderItem(1L, 1L, "사유", 7L, "SELLER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소할 수 없는 주문 상태");
        // 재고 복구 이벤트 미발행 (차감된 적 없으므로)
        then(applicationEventPublisher).should(never())
                .publishEvent(any(com.ecommerce.order.event.OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("C-3: 이미 취소된 항목 재취소 시 복구 이벤트 1회만 발행")
    void cancelOrderItem_idempotentEvent() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 1L, "사유", 7L, "SELLER");
        orderService.cancelOrderItem(1L, 1L, "사유 재시도", 7L, "SELLER"); // 같은 항목 재취소

        // 전이는 1회뿐 → 이벤트도 1회만
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(com.ecommerce.order.event.OrderItemCancelledApplicationEvent.class));
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

    /**
     * 여러 판매자 상품이 섞인 주문 생성 (sellerIds[i] 판매자, prices[i] 단가, 수량 1).
     * OrderItem id는 1부터 순번으로 부여 (취소 테스트에서 itemId로 사용).
     */
    private Order buildMultiSellerOrder(long[] sellerIds, long[] prices) {
        List<OrderItem> items = new java.util.ArrayList<>();
        long total = 0;
        for (int i = 0; i < sellerIds.length; i++) {
            OrderItem item = OrderItem.builder()
                    .productId(100L + i)
                    .productName("상품" + i)
                    .price(prices[i])
                    .quantity(1)
                    .sellerId(sellerIds[i])
                    .build();
            ReflectionTestUtils.setField(item, "id", (long) (i + 1));
            items.add(item);
            total += prices[i];
        }
        Order order = Order.builder()
                .userId(1L)
                .totalPrice(total)
                .items(items)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.confirm();   // 항목 취소 가능 상태(CONFIRMED) — 재고 차감 완료 가정 (C-2)
        return order;
    }
}
