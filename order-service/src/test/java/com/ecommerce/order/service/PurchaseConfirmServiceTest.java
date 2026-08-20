package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.AutoConfirmTarget;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseConfirmService 단위 테스트 (payment-foundation 3.2)")
class PurchaseConfirmServiceTest {

    private static final int  AUTO_CONFIRM_DAYS = 7;
    private static final int  BATCH_SIZE        = 500;
    private static final Long ORDER_ID          = 1L;
    private static final Long USER_ID           = 1L;

    @InjectMocks private PurchaseConfirmService purchaseConfirmService;

    @Mock private OrderRepository         orderRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private NotificationService     notificationService;
    @Mock private AutoConfirmExecutor     autoConfirmExecutor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(purchaseConfirmService, "autoConfirmDays", AUTO_CONFIRM_DAYS);
        ReflectionTestUtils.setField(purchaseConfirmService, "autoConfirmBatchSize", BATCH_SIZE);
    }

    // ── 수동 구매확정 ──────────────────────────────────────────────

    @Test
    @DisplayName("확정 — 배송완료 주문은 조건부 UPDATE 로 확정되고 알림이 생성된다")
    void confirm_delivered_success() {
        Order order = deliveredOrder();
        givenOrder(order);
        givenManualUpdateResult(1);

        OrderResponse response = purchaseConfirmService.confirm(ORDER_ID, USER_ID);

        assertThat(response.id()).isEqualTo(ORDER_ID);
        then(notificationService).should(times(1))
                .create(USER_ID, NotificationType.PURCHASE_CONFIRMED, ORDER_ID);
    }

    @Test
    @DisplayName("확정(H-3) — 확정은 조건부 UPDATE 경로를 통해서만 기록된다(읽기-수정-쓰기 금지)")
    void confirm_usesConditionalUpdate() {
        Order order = deliveredOrder();
        givenOrder(order);
        givenManualUpdateResult(1);

        purchaseConfirmService.confirm(ORDER_ID, USER_ID);

        ArgumentCaptor<Collection<OrderStatus>> statuses = statusCaptor();
        then(orderRepository).should(times(1)).confirmPurchaseNow(eq(ORDER_ID),
                eq(DeliveryStatus.DELIVERED), statuses.capture(), anyCollection(),
                any(LocalDateTime.class));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("확정(H-3) — 동시 확정 경합에서 밀리면(갱신 0건) 409, 알림도 발송하지 않는다")
    void confirm_lostRace_conflict() {
        Order order = deliveredOrder();
        givenOrder(order);
        givenManualUpdateResult(0);

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, USER_ID))
                .isInstanceOf(PurchaseAlreadyConfirmedException.class);
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("확정 — 배송완료 전 주문은 400 (UPDATE·알림 미실행)")
    void confirm_notDelivered_badRequest() {
        givenOrder(confirmedOrder());

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, USER_ID))
                .isInstanceOf(PurchaseConfirmNotAllowedException.class);
        thenNoConfirmation();
    }

    @Test
    @DisplayName("확정(H-1) — 전 항목 취소(CANCELLED)된 주문은 배송완료여도 400")
    void confirm_cancelledOrder_badRequest() {
        Order order = deliveredOrder();
        order.cancelItem(1L, "반품 승인");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        givenOrder(order);

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, USER_ID))
                .isInstanceOf(PurchaseConfirmNotAllowedException.class);
        thenNoConfirmation();
    }

    @Test
    @DisplayName("확정(H-2) — 진행 중 반품이 있는 주문은 400 (UPDATE·알림 미실행)")
    void confirm_pendingReturn_badRequest() {
        givenOrder(deliveredOrder());
        given(returnRequestRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection()))
                .willReturn(true);

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, USER_ID))
                .isInstanceOf(PurchaseConfirmNotAllowedException.class)
                .hasMessageContaining("반품");
        thenNoConfirmation();
    }

    @Test
    @DisplayName("확정(H-2) — 진행 중 반품 판정 기준은 REQUESTED/APPROVED (REFUNDED 는 확정을 막지 않는다)")
    void confirm_pendingReturnStatuses() {
        givenOrder(deliveredOrder());
        givenManualUpdateResult(1);

        purchaseConfirmService.confirm(ORDER_ID, USER_ID);

        ArgumentCaptor<Collection<ReturnStatus>> captor = returnStatusCaptor();
        then(returnRequestRepository).should()
                .existsByOrderIdAndStatusIn(eq(ORDER_ID), captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
    }

    @Test
    @DisplayName("확정 — 이미 확정된 주문은 409")
    void confirm_alreadyConfirmed_conflict() {
        Order order = deliveredOrder();
        ReflectionTestUtils.setField(order, "purchaseConfirmedAt", LocalDateTime.now());
        givenOrder(order);

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, USER_ID))
                .isInstanceOf(PurchaseAlreadyConfirmedException.class);
        thenNoConfirmation();
    }

    @Test
    @DisplayName("확정 — 타인 주문 확정 시도는 404 (정보 노출 방지)")
    void confirm_otherUserOrder_notFound() {
        givenOrder(deliveredOrder());

        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, 999L))
                .isInstanceOf(OrderNotFoundException.class);
        thenNoConfirmation();
    }

    @Test
    @DisplayName("확정 — 존재하지 않는 주문은 404")
    void confirm_orderNotFound() {
        given(orderRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseConfirmService.confirm(99L, USER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("확정 — 인증 정보(X-User-Id) 부재는 401 (주문 조회도 하지 않음)")
    void confirm_noUser_unauthorized() {
        assertThatThrownBy(() -> purchaseConfirmService.confirm(ORDER_ID, null))
                .isInstanceOf(UnauthorizedException.class);
        then(orderRepository).should(never()).findById(anyLong());
    }

    // ── 자동 구매확정 ──────────────────────────────────────────────

    @Test
    @DisplayName("자동확정 — 대상마다 건별 트랜잭션으로 확정한다")
    void autoConfirmExpired_confirmsTargets() {
        givenTargets(new AutoConfirmTarget(1L, 10L), new AutoConfirmTarget(2L, 20L));
        given(autoConfirmExecutor.confirmOne(any(), any(), any())).willReturn(true);

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isEqualTo(2);
        then(autoConfirmExecutor).should(times(2)).confirmOne(any(), any(), any());
    }

    @Test
    @DisplayName("자동확정(H-1·H-2) — 조회 조건에 확정 대상 주문상태와 진행 중 반품 제외가 포함된다")
    void autoConfirmExpired_filtersByStatusAndPendingReturn() {
        givenTargets();

        purchaseConfirmService.autoConfirmExpired();

        ArgumentCaptor<Collection<OrderStatus>> orderStatuses = statusCaptor();
        ArgumentCaptor<Collection<ReturnStatus>> returnStatuses = returnStatusCaptor();
        then(orderRepository).should().findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                orderStatuses.capture(), returnStatuses.capture(),
                any(LocalDateTime.class), any(Pageable.class));
        assertThat(orderStatuses.getValue())
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);
        assertThat(returnStatuses.getValue())
                .containsExactlyInAnyOrder(ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
    }

    @Test
    @DisplayName("자동확정 — 조회 기준 시각은 현재로부터 기준일(7일) 이전")
    void autoConfirmExpired_usesConfiguredThreshold() {
        LocalDateTime before = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        givenTargets();

        purchaseConfirmService.autoConfirmExpired();

        LocalDateTime after = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(orderRepository).should().findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                anyCollection(), anyCollection(), captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("자동확정 — 대상이 없으면 확정도 알림도 없다")
    void autoConfirmExpired_noTargets() {
        givenTargets();

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isZero();
        then(autoConfirmExecutor).should(never()).confirmOne(any(), any(), any());
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("자동확정 — 다른 인스턴스가 선점(갱신 0건)하면 확정 건수에 포함하지 않는다")
    void autoConfirmExpired_lostRace_notCounted() {
        givenTargets(new AutoConfirmTarget(1L, 10L));
        given(autoConfirmExecutor.confirmOne(any(), any(), any())).willReturn(false);

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isZero();
    }

    @Test
    @DisplayName("자동확정(M-1) — 한 건이 실패해도 나머지 대상은 계속 처리된다")
    void autoConfirmExpired_isolatesFailure() {
        AutoConfirmTarget failing = new AutoConfirmTarget(1L, 10L);
        AutoConfirmTarget healthy = new AutoConfirmTarget(2L, 20L);
        givenTargets(failing, healthy);
        willThrow(new IllegalStateException("확정 실패"))
                .given(autoConfirmExecutor).confirmOne(eq(failing), any(), any());
        given(autoConfirmExecutor.confirmOne(eq(healthy), any(), any())).willReturn(true);

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isEqualTo(1);
        then(autoConfirmExecutor).should(times(1)).confirmOne(eq(healthy), any(), any());
    }

    @Test
    @DisplayName("자동확정 — 배치 크기만큼만 조회해 대량 적체 시 처리량을 제한한다")
    void autoConfirmExpired_limitsBatchSize() {
        givenTargets();

        purchaseConfirmService.autoConfirmExpired();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(orderRepository).should().findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                anyCollection(), anyCollection(), any(LocalDateTime.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
    }

    // ── helpers ──────────────────────────────────────────────────

    private void givenOrder(Order order) {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    }

    private void givenManualUpdateResult(int updatedRows) {
        given(orderRepository.confirmPurchaseNow(eq(ORDER_ID), eq(DeliveryStatus.DELIVERED),
                anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .willReturn(updatedRows);
    }

    private void givenTargets(AutoConfirmTarget... targets) {
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED), anyCollection(),
                anyCollection(), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(targets));
    }

    /** 확정 UPDATE 도 알림도 실행되지 않았는지 */
    private void thenNoConfirmation() {
        then(orderRepository).should(never()).confirmPurchaseNow(anyLong(), any(),
                anyCollection(), anyCollection(), any(LocalDateTime.class));
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<OrderStatus>> statusCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<ReturnStatus>> returnStatusCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    /** 재고 차감 완료(CONFIRMED) 주문 — 배송상태 PREPARING */
    private Order confirmedOrder() {
        List<OrderItem> items = new ArrayList<>();
        OrderItem item = OrderItem.builder()
                .productId(100L).productName("상품").price(20_000L).quantity(1).sellerId(7L)
                .build();
        ReflectionTestUtils.setField(item, "id", 1L);
        items.add(item);

        Order order = Order.builder().userId(USER_ID).totalPrice(20_000L).items(items).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid();
        order.confirm();
        return order;
    }

    /** 배송완료(DELIVERED) 주문 — 구매확정 자격 충족 상태 */
    private Order deliveredOrder() {
        Order order = confirmedOrder();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        return order;
    }
}
