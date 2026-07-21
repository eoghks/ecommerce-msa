package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Address;
import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.request.OrderItemRequest;
import com.ecommerce.order.dto.response.FailedOrderResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderItemCancelledApplicationEvent;
import com.ecommerce.order.exception.DeliveryStatusAccessDeniedException;
import com.ecommerce.order.exception.InvalidDeliveryStatusException;
import com.ecommerce.order.exception.InvalidOrderShippingException;
import com.ecommerce.order.exception.OrderItemAccessDeniedException;
import com.ecommerce.order.exception.OrderItemNotFoundException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.AddressRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock private com.ecommerce.order.repository.FailedOrderLogRepository failedOrderLogRepository;
    @Mock private AddressRepository         addressRepository;
    @Mock private NotificationService       notificationService;

    // ── 주문 생성 ──────────────────────────────────────────────────

    @Test
    @DisplayName("주문 생성 — 상품 조회 후 영속 빈(saveAndPublish)에 위임 (프록시 경유 트랜잭션 보장)")
    void createOrder_success() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 2)),
                null, "홍길동", "010-1234-5678", "서울시 강남구 테헤란로 1"
        );
        ProductClient.ProductInfo productInfo =
                new ProductClient.ProductInfo(10L, "갤럭시 S24", 1_200_000L, 10, "https://example.com/img.jpg", 7L);
        Order savedOrder = buildOrder(userId, 10L, "갤럭시 S24", 1_200_000L, 2);
        OrderResponse expected = OrderResponse.from(savedOrder);

        given(productClient.getProduct(10L)).willReturn(productInfo);
        // 가격은 서버(ProductClient)에서 조회한 값으로 계산: 1,200,000 × 2
        given(orderPersistenceService.saveAndPublish(eq(userId), eq(2_400_000L), anyList(), any(ShippingInfo.class)))
                .willReturn(expected);

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // 자기호출 제거 검증: 별도 빈에 위임됐는지 (직접입력 배송지 스냅샷 전달)
        then(orderPersistenceService).should(times(1))
                .saveAndPublish(eq(userId), eq(2_400_000L), anyList(),
                        eq(new ShippingInfo("홍길동", "010-1234-5678", "서울시 강남구 테헤란로 1")));
    }

    // ── V1.1-3: 주문 배송지 스냅샷 ────────────────────────────────────

    @Test
    @DisplayName("createOrder — addressId 지정 시 본인 소유 주소 값을 스냅샷으로 복사")
    void createOrder_withAddressId_snapshot() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 1)), 5L, null, null, null);
        ProductClient.ProductInfo productInfo =
                new ProductClient.ProductInfo(10L, "상품", 1_000L, 10, null, 7L);
        Address address = Address.builder()
                .userId(userId).receiver("김철수").phone("010-9999-8888")
                .address("부산시 해운대구").isDefault(true).build();

        given(productClient.getProduct(10L)).willReturn(productInfo);
        given(addressRepository.findById(5L)).willReturn(Optional.of(address));
        given(orderPersistenceService.saveAndPublish(eq(userId), eq(1_000L), anyList(), any(ShippingInfo.class)))
                .willReturn(OrderResponse.from(buildOrder(userId, 10L, "상품", 1_000L, 1)));

        orderService.createOrder(userId, request);

        // 주소록 값이 그대로 스냅샷으로 전달돼야 함
        then(orderPersistenceService).should(times(1))
                .saveAndPublish(eq(userId), eq(1_000L), anyList(),
                        eq(new ShippingInfo("김철수", "010-9999-8888", "부산시 해운대구")));
    }

    @Test
    @DisplayName("createOrder — 타인 소유/존재하지 않는 addressId → 400")
    void createOrder_invalidAddressId_badRequest() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 1)), 99L, null, null, null);

        given(addressRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(InvalidOrderShippingException.class);
        then(orderPersistenceService).should(never())
                .saveAndPublish(any(), anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("createOrder — addressId 없고 직접입력도 누락 → 400")
    void createOrder_noShipping_badRequest() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 1)), null, null, "", "  ");

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(InvalidOrderShippingException.class);
        then(orderPersistenceService).should(never())
                .saveAndPublish(any(), anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("createOrder — addressId 없이 직접입력 하위호환 정상 동작")
    void createOrder_directInput_backwardCompatible() {
        Long userId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(10L, 1)), null, "홍길동", "010-1111-2222", "서울시 종로구");
        ProductClient.ProductInfo productInfo =
                new ProductClient.ProductInfo(10L, "상품", 1_000L, 10, null, 7L);

        given(productClient.getProduct(10L)).willReturn(productInfo);
        given(orderPersistenceService.saveAndPublish(eq(userId), eq(1_000L), anyList(), any(ShippingInfo.class)))
                .willReturn(OrderResponse.from(buildOrder(userId, 10L, "상품", 1_000L, 1)));

        orderService.createOrder(userId, request);

        // 주소록 조회 없이 직접입력 값으로 스냅샷
        then(addressRepository).should(never()).findById(any());
        then(orderPersistenceService).should(times(1))
                .saveAndPublish(eq(userId), eq(1_000L), anyList(),
                        eq(new ShippingInfo("홍길동", "010-1111-2222", "서울시 종로구")));
    }

    // ── V1.1-3: 배송상태 변경 ────────────────────────────────────────

    @Test
    @DisplayName("updateDeliveryStatus — ADMIN, PREPARING→SHIPPING 정상 전이")
    void updateDeliveryStatus_admin_advance() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 999L, "ADMIN");

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("updateDeliveryStatus — 본인 상품 포함 SELLER 허용")
    void updateDeliveryStatus_seller_owns_allowed() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 7L, "SELLER");

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("updateDeliveryStatus — 본인 상품 없는 SELLER → 403")
    void updateDeliveryStatus_seller_notOwner_forbidden() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 8L, "SELLER"))
                .isInstanceOf(DeliveryStatusAccessDeniedException.class);
    }

    @Test
    @DisplayName("updateDeliveryStatus — USER → 403")
    void updateDeliveryStatus_user_forbidden() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 7L, "USER"))
                .isInstanceOf(DeliveryStatusAccessDeniedException.class);
    }

    @Test
    @DisplayName("updateDeliveryStatus — 역행 전이(SHIPPING→PREPARING) → 400")
    void updateDeliveryStatus_backward_badRequest() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, DeliveryStatus.PREPARING, 999L, "ADMIN"))
                .isInstanceOf(InvalidDeliveryStatusException.class);
    }

    @Test
    @DisplayName("updateDeliveryStatus — 건너뜀 전이(PREPARING→DELIVERED) → 400")
    void updateDeliveryStatus_skip_badRequest() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, DeliveryStatus.DELIVERED, 999L, "ADMIN"))
                .isInstanceOf(InvalidDeliveryStatusException.class);
    }

    @Test
    @DisplayName("updateDeliveryStatus — PENDING 주문(대상 아님) → 400")
    void updateDeliveryStatus_pending_badRequest() {
        // confirm 안 한 PENDING 주문
        Order order = Order.builder().userId(1L).totalPrice(20_000L)
                .items(List.of(OrderItem.builder()
                        .productId(100L).productName("상품").price(20_000L).quantity(1).sellerId(7L).build()))
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 999L, "ADMIN"))
                .isInstanceOf(InvalidDeliveryStatusException.class);
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
    @DisplayName("cancelByUser — PENDING(미차감) 주문 취소 성공, 재고 복구 이벤트 없음")
    void cancelByUser_pending_success() {
        Long userId = 1L;
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelByUser(1L, userId, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // PENDING은 미차감 → 복구 이벤트 미발행
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("M-N3: cancelByUser — CONFIRMED 주문 취소 시 CANCELLED + 활성 항목마다 복구 이벤트 발행")
    void cancelByUser_confirmed_restockEvents() {
        // 판매자 7·8 항목 2개, confirm 완료(차감) 상태
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelByUser(1L, 1L, "단순 변심");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 활성 항목 2개 → 복구 이벤트 2회
        then(applicationEventPublisher).should(times(2))
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("M-N3: cancelByUser — 부분취소 주문 취소 시 남은 활성 항목만 복구 이벤트 (중복 방지)")
    void cancelByUser_partiallyCancelled_onlyActiveRestock() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        // 이미 item1 취소됨(PARTIALLY_CANCELLED) — item1은 복구 대상 아님
        orderService.cancelOrderItem(1L, 1L, "사전 취소", 7L, "SELLER");

        orderService.cancelByUser(1L, 1L, null);
        // 잔여 활성 항목까지 취소되어 전체 CANCELLED — 이후 재취소는 멱등 no-op
        orderService.cancelByUser(1L, 1L, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // item1(항목취소 1회) + item2(사용자취소 1회) = 총 2회 — item1 재발행·재취소 재발행 없음
        then(applicationEventPublisher).should(times(2))
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("E2E S5: cancelByUser — 이미 CANCELLED 주문 재취소는 멱등 no-op (예외 없음, 복구 이벤트 미발행)")
    void cancelByUser_alreadyCancelled_idempotentNoOp() {
        Long userId = 1L;
        Order order = buildOrder(userId, 10L, "상품", 10_000L, 1);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // 예외 없이 성공 처리(no-op)
        orderService.cancelByUser(1L, userId, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 이미 활성 항목이 없으므로 재고 복구 이벤트 미발행 (중복 복구 방지)
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("E2E S5: cancelByUser — CONFIRMED 취소 후 재취소는 멱등 no-op (복구 이벤트 재발행 없음)")
    void cancelByUser_confirmed_thenReCancel_idempotent() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // 1회차 취소 — 활성 항목 2개 복구 이벤트
        orderService.cancelByUser(1L, 1L, null);
        // 2회차 재취소 — 이미 CANCELLED → no-op, 추가 발행 없음
        orderService.cancelByUser(1L, 1L, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(applicationEventPublisher).should(times(2))
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("cancelByUser — 타인 주문 취소 시도 → 404")
    void cancelByUser_otherUser() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByUser(1L, 999L, null))
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

    // ── M-3: 실패(자동취소) 주문 ────────────────────────────────────

    @Test
    @DisplayName("M-3: cancelOrder(Saga) — 자동취소 시 실패 로그 1건 기록")
    void cancelOrder_recordsFailedLog() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족: internal-detail");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(failedOrderLogRepository).should(times(1))
                .save(any(com.ecommerce.order.domain.FailedOrderLog.class));
    }

    @Test
    @DisplayName("M-3: cancelOrder — 이미 CANCELLED면 실패 로그 중복 기록 안 함")
    void cancelOrder_idempotent_noDuplicateLog() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족");

        then(failedOrderLogRepository).should(never())
                .save(any(com.ecommerce.order.domain.FailedOrderLog.class));
    }

    @Test
    @DisplayName("M-3: getFailedOrders — 최근 발생 순 페이징 조회 (ADMIN)")
    void getFailedOrders_success() {
        PageRequest pageable = PageRequest.of(0, 20);
        com.ecommerce.order.domain.FailedOrderLog logEntry =
                com.ecommerce.order.domain.FailedOrderLog.builder()
                        .orderId(1L).userId(5L).reason("재고 확보 실패(자동취소)").build();
        Page<com.ecommerce.order.domain.FailedOrderLog> page =
                new PageImpl<>(List.of(logEntry), pageable, 1);
        given(failedOrderLogRepository.findAllByOrderByOccurredAtDesc(pageable)).willReturn(page);

        Page<FailedOrderResponse> result = orderService.getFailedOrders(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).orderId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).reason()).isEqualTo("재고 확보 실패(자동취소)");
    }

    // ── V1.1-4: 알림 생성 트리거 ──────────────────────────────────────

    @Test
    @DisplayName("알림: confirmOrder — PENDING→CONFIRMED 전이 시 ORDER_CONFIRMED 생성")
    void notify_confirmOrder() {
        Order order = buildOrder(5L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.confirmOrder(1L);

        then(notificationService).should(times(1))
                .create(5L, NotificationType.ORDER_CONFIRMED, 1L);
    }

    @Test
    @DisplayName("알림: confirmOrder — 이미 CONFIRMED(멱등)면 알림 미생성")
    void notify_confirmOrder_idempotent_noNotify() {
        Order order = buildOrder(5L, 10L, "상품", 10_000L, 1);
        order.confirm();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.confirmOrder(1L);

        then(notificationService).should(never())
                .create(any(), any(), any());
    }

    @Test
    @DisplayName("알림: cancelOrder(Saga 자동취소) — 실제 취소 전이 시 ORDER_CANCELLED 생성")
    void notify_cancelOrder_saga() {
        Order order = buildOrder(5L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족");

        then(notificationService).should(times(1))
                .create(5L, NotificationType.ORDER_CANCELLED, 1L);
    }

    @Test
    @DisplayName("알림: cancelOrder — 이미 CANCELLED(멱등)면 알림 미생성")
    void notify_cancelOrder_idempotent_noNotify() {
        Order order = buildOrder(5L, 10L, "상품", 10_000L, 1);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrder(1L, "재고 부족");

        then(notificationService).should(never())
                .create(any(), any(), any());
    }

    @Test
    @DisplayName("알림: cancelByUser — 사용자 취소 시 ORDER_CANCELLED 생성")
    void notify_cancelByUser() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelByUser(1L, 1L, null);

        then(notificationService).should(times(1))
                .create(1L, NotificationType.ORDER_CANCELLED, 1L);
    }

    @Test
    @DisplayName("알림: cancelByUser — 이미 취소된 주문 재취소(no-op)면 알림 미생성")
    void notify_cancelByUser_alreadyCancelled_noNotify() {
        Order order = buildOrder(1L, 10L, "상품", 10_000L, 1);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelByUser(1L, 1L, null);

        then(notificationService).should(never())
                .create(any(), any(), any());
    }

    @Test
    @DisplayName("알림: cancelOrderItem — 실제 항목 취소 전이 시 ORDER_ITEM_CANCELLED 생성")
    void notify_cancelOrderItem() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 1L, "재고 소진", 7L, "SELLER");

        then(notificationService).should(times(1))
                .create(order.getUserId(), NotificationType.ORDER_ITEM_CANCELLED, 1L);
    }

    @Test
    @DisplayName("알림: cancelOrderItem — 이미 취소된 항목 재취소(멱등)면 알림 미생성")
    void notify_cancelOrderItem_idempotent_noNotify() {
        Order order = buildMultiSellerOrder(new long[]{7L, 8L}, new long[]{20_000L, 50_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.cancelOrderItem(1L, 1L, "사유", 7L, "SELLER");
        orderService.cancelOrderItem(1L, 1L, "재시도", 7L, "SELLER");

        then(notificationService).should(times(1))
                .create(order.getUserId(), NotificationType.ORDER_ITEM_CANCELLED, 1L);
    }

    @Test
    @DisplayName("알림: updateDeliveryStatus — SHIPPING 전이 시 DELIVERY_SHIPPING 생성")
    void notify_deliveryShipping() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.updateDeliveryStatus(1L, DeliveryStatus.SHIPPING, 999L, "ADMIN");

        then(notificationService).should(times(1))
                .create(order.getUserId(), NotificationType.DELIVERY_SHIPPING, 1L);
    }

    @Test
    @DisplayName("알림: updateDeliveryStatus — DELIVERED 전이 시 DELIVERY_DELIVERED 생성")
    void notify_deliveryDelivered() {
        Order order = buildMultiSellerOrder(new long[]{7L}, new long[]{20_000L});
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.updateDeliveryStatus(1L, DeliveryStatus.DELIVERED, 999L, "ADMIN");

        then(notificationService).should(times(1))
                .create(order.getUserId(), NotificationType.DELIVERY_DELIVERED, 1L);
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
