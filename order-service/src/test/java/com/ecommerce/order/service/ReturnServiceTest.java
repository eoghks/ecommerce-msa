package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.response.ReturnResponse;
import com.ecommerce.order.event.OrderItemCancelledApplicationEvent;
import com.ecommerce.order.exception.DuplicateReturnRequestException;
import com.ecommerce.order.exception.InvalidReturnStatusException;
import com.ecommerce.order.exception.OrderItemNotFoundException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.ReturnAccessDeniedException;
import com.ecommerce.order.exception.ReturnNotAllowedException;
import com.ecommerce.order.exception.ReturnRequestNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ReturnRequestRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnService 단위 테스트 (V1.1-5)")
class ReturnServiceTest {

    @InjectMocks
    private ReturnService returnService;

    @Mock private ReturnRequestRepository   returnRequestRepository;
    @Mock private OrderRepository           orderRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private NotificationService       notificationService;

    // ── 반품 신청 ──────────────────────────────────────────────────

    @Test
    @DisplayName("신청 — 배송완료 + ACTIVE 항목 성공 (REQUESTED 저장)")
    void request_success() {
        Order order = deliveredOrder();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(returnRequestRepository.existsByOrderItemIdAndStatusIn(anyLong(), anyCollection()))
                .willReturn(false);
        given(returnRequestRepository.save(any(ReturnRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReturnResponse response = returnService.request(1L, 1L, 1L, "제품 하자");

        assertThat(response.status()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(response.orderItemId()).isEqualTo(1L);
        assertThat(response.reason()).isEqualTo("제품 하자");
        then(returnRequestRepository).should(times(1)).save(any(ReturnRequest.class));
    }

    @Test
    @DisplayName("신청 — 배송완료(DELIVERED) 아닌 주문 → 400")
    void request_notDelivered_badRequest() {
        Order order = confirmedOrder();   // PREPARING
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> returnService.request(1L, 1L, 1L, "단순 변심"))
                .isInstanceOf(ReturnNotAllowedException.class);
        then(returnRequestRepository).should(never()).save(any(ReturnRequest.class));
    }

    @Test
    @DisplayName("신청 — 이미 CANCELLED 된 항목 → 400")
    void request_cancelledItem_badRequest() {
        Order order = deliveredOrder();
        order.cancelItem(1L, "판매자 취소");
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> returnService.request(1L, 1L, 1L, "제품 하자"))
                .isInstanceOf(ReturnNotAllowedException.class);
        assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("신청 — 타인 주문 반품 시도 → 404")
    void request_otherUserOrder_notFound() {
        Order order = deliveredOrder();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> returnService.request(1L, 1L, 999L, "제품 하자"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("신청 — 존재하지 않는 항목 → 404")
    void request_itemNotFound() {
        Order order = deliveredOrder();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> returnService.request(1L, 99L, 1L, "제품 하자"))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    @DisplayName("신청 — 동일 항목 중복 진행(REQUESTED/APPROVED/REFUNDED) → 409")
    void request_duplicate_conflict() {
        Order order = deliveredOrder();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(returnRequestRepository.existsByOrderItemIdAndStatusIn(anyLong(), anyCollection()))
                .willReturn(true);

        assertThatThrownBy(() -> returnService.request(1L, 1L, 1L, "제품 하자"))
                .isInstanceOf(DuplicateReturnRequestException.class);
        then(returnRequestRepository).should(never()).save(any(ReturnRequest.class));
    }

    @Test
    @DisplayName("신청 — 사유 누락(빈 값) → 400")
    void request_blankReason_badRequest() {
        assertThatThrownBy(() -> returnService.request(1L, 1L, 1L, "  "))
                .isInstanceOf(ReturnNotAllowedException.class);
        then(returnRequestRepository).should(never()).save(any(ReturnRequest.class));
    }

    @Test
    @DisplayName("신청 — X-User-Id 없음 → 401")
    void request_noUser_unauthorized() {
        assertThatThrownBy(() -> returnService.request(1L, 1L, null, "제품 하자"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 반품 승인 ──────────────────────────────────────────────────

    @Test
    @DisplayName("승인 — 항목 CANCELLED + 재고복구 이벤트 1회 + REFUNDED 전이 + 알림 생성")
    void approve_success() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        ReturnResponse response = returnService.approve(10L, 999L, "ADMIN");

        assertThat(response.status()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(response.processedAt()).isNotNull();
        assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        // 재고 복구 이벤트는 실제 전이 1회분만 발행
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
        then(notificationService).should(times(1))
                .create(1L, NotificationType.RETURN_APPROVED, 1L);
        then(notificationService).should(times(1))
                .create(1L, NotificationType.RETURN_REFUNDED, 1L);
    }

    @Test
    @DisplayName("승인 — 승인 시 주문 상태 재계산(전 항목 반품 → CANCELLED, 합계 0)")
    void approve_recalculatesOrder() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        returnService.approve(10L, 999L, "ADMIN");

        assertThat(order.getTotalPrice()).isEqualTo(0L);
    }

    @Test
    @DisplayName("승인 — REQUESTED 아닌 반품(이미 REFUNDED) 재승인 → 400, 재고복구 이벤트 미발행")
    void approve_notRequested_badRequest() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        returnService.approve(10L, 999L, "ADMIN");

        assertThatThrownBy(() -> returnService.approve(10L, 999L, "ADMIN"))
                .isInstanceOf(InvalidReturnStatusException.class);
        // 중복 복구 없음 — 이벤트는 최초 1회만
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("승인 — 거부(REJECTED)된 반품 승인 시도 → 400")
    void approve_rejected_badRequest() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);
        returnService.reject(10L, 999L, "ADMIN", "반품 기한 초과");

        assertThatThrownBy(() -> returnService.approve(10L, 999L, "ADMIN"))
                .isInstanceOf(InvalidReturnStatusException.class);
    }

    // ── 반품 거부 ──────────────────────────────────────────────────

    @Test
    @DisplayName("거부 — REJECTED 전이 + 처리시각·사유 기록 + 알림 생성")
    void reject_success() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        ReturnResponse response = returnService.reject(10L, 999L, "ADMIN", "사용 흔적 있음");

        assertThat(response.status()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("사용 흔적 있음");
        assertThat(response.processedAt()).isNotNull();
        then(notificationService).should(times(1))
                .create(1L, NotificationType.RETURN_REJECTED, 1L);
        // 거부는 재고 복구 대상 아님
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("거부 — 사유 누락 → 400")
    void reject_blankReason_badRequest() {
        assertThatThrownBy(() -> returnService.reject(10L, 999L, "ADMIN", ""))
                .isInstanceOf(ReturnNotAllowedException.class);
        then(returnRequestRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("거부 — REQUESTED 아닌 반품 거부 시도 → 400")
    void reject_notRequested_badRequest() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);
        returnService.reject(10L, 999L, "ADMIN", "사용 흔적 있음");

        assertThatThrownBy(() -> returnService.reject(10L, 999L, "ADMIN", "재확인"))
                .isInstanceOf(InvalidReturnStatusException.class);
    }

    @Test
    @DisplayName("거부 후 재신청 허용 — REJECTED 는 활성 집합에서 제외되므로 신규 신청 성공")
    void reject_thenReRequest_allowed() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);
        returnService.reject(10L, 999L, "ADMIN", "사유 불충분");

        // 거부 상태는 활성 집합(REQUESTED/APPROVED/REFUNDED)에 없으므로 중복 판정 false
        given(returnRequestRepository.existsByOrderItemIdAndStatusIn(anyLong(), anyCollection()))
                .willReturn(false);
        given(returnRequestRepository.save(any(ReturnRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReturnResponse response = returnService.request(1L, 1L, 1L, "사유 보완 후 재신청");

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(response.status()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    @DisplayName("거부 — 없는 반품 → 404")
    void reject_notFound() {
        given(returnRequestRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.reject(99L, 999L, "ADMIN", "사유"))
                .isInstanceOf(ReturnRequestNotFoundException.class);
    }

    // ── 권한 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("권한 — 해당 SELLER(본인 상품 포함 주문) 승인 허용")
    void approve_ownerSeller_allowed() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        ReturnResponse response = returnService.approve(10L, 7L, "SELLER");

        assertThat(response.status()).isEqualTo(ReturnStatus.REFUNDED);
    }

    @Test
    @DisplayName("권한 — 본인 상품 없는 타 SELLER → 403")
    void approve_otherSeller_forbidden() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        assertThatThrownBy(() -> returnService.approve(10L, 8L, "SELLER"))
                .isInstanceOf(ReturnAccessDeniedException.class);
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
    }

    @Test
    @DisplayName("권한(H-1) — 멀티셀러 주문에서 타 판매자 항목 승인 시도 → 403, 재고복구·환불 미실행")
    void approve_otherSellerItemInMultiSellerOrder_forbidden() {
        Order order = multiSellerDeliveredOrder();
        // 반품 대상은 판매자 8 의 항목(id=2)
        ReturnRequest returnRequest = requestedReturn(1L, 2L, 1L);
        givenReturnAndOrder(returnRequest, order);

        // 같은 주문에 자기 상품(id=1)이 있는 판매자 7 이 타 판매자 항목 반품을 승인 시도
        assertThatThrownBy(() -> returnService.approve(10L, 7L, "SELLER"))
                .isInstanceOf(ReturnAccessDeniedException.class);
        assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getItems().get(1).getStatus()).isEqualTo(OrderItemStatus.ACTIVE);
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
        then(notificationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("권한(H-1) — 멀티셀러 주문에서 대상 항목 소유 판매자 승인 → 성공")
    void approve_ownerSellerItemInMultiSellerOrder_allowed() {
        Order order = multiSellerDeliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 2L, 1L);
        givenReturnAndOrder(returnRequest, order);

        ReturnResponse response = returnService.approve(10L, 8L, "SELLER");

        assertThat(response.status()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(order.getItems().get(1).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        // 타 판매자 항목(id=1)은 영향 없음
        assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.ACTIVE);
    }

    @Test
    @DisplayName("권한(H-1) — 멀티셀러 주문에서 타 판매자 항목 거부 시도 → 403")
    void reject_otherSellerItemInMultiSellerOrder_forbidden() {
        Order order = multiSellerDeliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 2L, 1L);
        givenReturnAndOrder(returnRequest, order);

        assertThatThrownBy(() -> returnService.reject(10L, 7L, "SELLER", "사용 흔적 있음"))
                .isInstanceOf(ReturnAccessDeniedException.class);
        assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    @DisplayName("승인(M-2) — 신청 후 다른 경로로 취소된 항목 → 400, 재고복구·환불 미실행")
    void approve_alreadyCancelledItem_badRequest() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);
        order.cancelItem(1L, "사용자 주문 취소");   // 신청 이후 다른 경로로 취소

        assertThatThrownBy(() -> returnService.approve(10L, 999L, "ADMIN"))
                .isInstanceOf(ReturnNotAllowedException.class);
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderItemCancelledApplicationEvent.class));
        then(notificationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("승인(M-4) — 환불 시각은 refundedAt 에 기록되고 승인 시각(processedAt)은 보존")
    void approve_keepsApprovedAtSeparateFromRefundedAt() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        returnService.approve(10L, 999L, "ADMIN");

        assertThat(returnRequest.getProcessedAt()).isNotNull();
        assertThat(returnRequest.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("권한 — 일반 USER 승인/거부 시도 → 403")
    void approve_user_forbidden() {
        Order order = deliveredOrder();
        ReturnRequest returnRequest = requestedReturn(1L, 1L, 1L);
        givenReturnAndOrder(returnRequest, order);

        assertThatThrownBy(() -> returnService.approve(10L, 1L, "USER"))
                .isInstanceOf(ReturnAccessDeniedException.class);
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("조회 — 내 반품 목록은 본인 것만 (최신순 페이징)")
    void getMyReturns_ownOnly() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ReturnRequest> page = new PageImpl<>(List.of(requestedReturn(1L, 1L, 1L)), pageable, 1);
        given(returnRequestRepository.findByUserIdOrderByRequestedAtDesc(1L, pageable))
                .willReturn(page);

        Page<ReturnResponse> result = returnService.getMyReturns(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("조회 — 내 반품 목록 X-User-Id 없음 → 401")
    void getMyReturns_noUser_unauthorized() {
        assertThatThrownBy(() -> returnService.getMyReturns(null, PageRequest.of(0, 20)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("조회 — ADMIN 은 전체 반품 조회")
    void getManagedReturns_admin_all() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ReturnRequest> page = new PageImpl<>(List.of(requestedReturn(1L, 1L, 1L)), pageable, 1);
        given(returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable)).willReturn(page);

        Page<ReturnResponse> result = returnService.getManagedReturns(999L, "ADMIN", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        then(returnRequestRepository).should(never()).findBySellerId(anyLong(), any());
    }

    @Test
    @DisplayName("조회 — SELLER 는 본인 상품 포함 건만 조회")
    void getManagedReturns_seller_ownOnly() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ReturnRequest> page = new PageImpl<>(List.of(requestedReturn(1L, 1L, 1L)), pageable, 1);
        given(returnRequestRepository.findBySellerId(7L, pageable)).willReturn(page);

        Page<ReturnResponse> result = returnService.getManagedReturns(7L, "SELLER", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        then(returnRequestRepository).should(never()).findAllByOrderByRequestedAtDesc(any());
    }

    @Test
    @DisplayName("조회 — 일반 USER 관리 목록 조회 → 403")
    void getManagedReturns_user_forbidden() {
        assertThatThrownBy(() ->
                returnService.getManagedReturns(1L, "USER", PageRequest.of(0, 20)))
                .isInstanceOf(ReturnAccessDeniedException.class);
    }

    // ── helper ──────────────────────────────────────────────────────

    /** 반품 + 주문 조회 스텁 (returnId=10) */
    private void givenReturnAndOrder(ReturnRequest returnRequest, Order order) {
        given(returnRequestRepository.findById(10L)).willReturn(Optional.of(returnRequest));
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
    }

    /** REQUESTED 상태 반품 요청 (id=10) */
    private ReturnRequest requestedReturn(Long orderId, Long itemId, Long userId) {
        ReturnRequest returnRequest = ReturnRequest.builder()
                .orderId(orderId)
                .orderItemId(itemId)
                .userId(userId)
                .reason("제품 하자")
                .build();
        ReflectionTestUtils.setField(returnRequest, "id", 10L);
        return returnRequest;
    }

    /** 재고 차감 완료(CONFIRMED) 주문 — 배송상태 PREPARING */
    private Order confirmedOrder() {
        List<OrderItem> items = new ArrayList<>();
        OrderItem item = OrderItem.builder()
                .productId(100L).productName("상품").price(20_000L).quantity(1).sellerId(7L)
                .build();
        ReflectionTestUtils.setField(item, "id", 1L);
        items.add(item);

        Order order = Order.builder().userId(1L).totalPrice(20_000L).items(items).build();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.confirm();
        return order;
    }

    /** 배송완료(DELIVERED) 주문 — 반품 자격 충족 상태 */
    private Order deliveredOrder() {
        Order order = confirmedOrder();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        return order;
    }

    /** 멀티 셀러 배송완료 주문 — 항목1(판매자 7), 항목2(판매자 8) */
    private Order multiSellerDeliveredOrder() {
        List<OrderItem> items = new ArrayList<>();
        items.add(itemOf(1L, 100L, 7L));
        items.add(itemOf(2L, 200L, 8L));

        Order order = Order.builder().userId(1L).totalPrice(40_000L).items(items).build();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.confirm();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        return order;
    }

    /** 주문 항목 생성 — id 는 영속 상태를 모사해 직접 주입 */
    private OrderItem itemOf(Long id, Long productId, Long sellerId) {
        OrderItem item = OrderItem.builder()
                .productId(productId).productName("상품").price(20_000L).quantity(1).sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
