package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.AutoConfirmTarget;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseConfirmService 단위 테스트 (payment-foundation 3.2)")
class PurchaseConfirmServiceTest {

    private static final int AUTO_CONFIRM_DAYS = 7;
    private static final int BATCH_SIZE        = 500;

    @InjectMocks private PurchaseConfirmService purchaseConfirmService;

    @Mock private OrderRepository     orderRepository;
    @Mock private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(purchaseConfirmService, "autoConfirmDays", AUTO_CONFIRM_DAYS);
        ReflectionTestUtils.setField(purchaseConfirmService, "autoConfirmBatchSize", BATCH_SIZE);
    }

    // ── 수동 구매확정 ──────────────────────────────────────────────

    @Test
    @DisplayName("확정 — 배송완료 주문은 확정 시각이 기록되고 알림이 생성된다")
    void confirm_delivered_success() {
        Order order = deliveredOrder();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        OrderResponse response = purchaseConfirmService.confirm(1L, 1L);

        assertThat(response.purchaseConfirmedAt()).isNotNull();
        assertThat(order.isPurchaseConfirmed()).isTrue();
        then(notificationService).should(times(1))
                .create(1L, NotificationType.PURCHASE_CONFIRMED, 1L);
    }

    @Test
    @DisplayName("확정 — 배송완료 전 주문은 400 (알림 미생성)")
    void confirm_notDelivered_badRequest() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(confirmedOrder()));

        assertThatThrownBy(() -> purchaseConfirmService.confirm(1L, 1L))
                .isInstanceOf(PurchaseConfirmNotAllowedException.class);
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("확정 — 이미 확정된 주문은 409")
    void confirm_alreadyConfirmed_conflict() {
        Order order = deliveredOrder();
        order.confirmPurchase(LocalDateTime.now());
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> purchaseConfirmService.confirm(1L, 1L))
                .isInstanceOf(PurchaseAlreadyConfirmedException.class);
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("확정 — 타인 주문 확정 시도는 404 (정보 노출 방지)")
    void confirm_otherUserOrder_notFound() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(deliveredOrder()));

        assertThatThrownBy(() -> purchaseConfirmService.confirm(1L, 999L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("확정 — 존재하지 않는 주문은 404")
    void confirm_orderNotFound() {
        given(orderRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseConfirmService.confirm(99L, 1L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("확정 — 인증 정보(X-User-Id) 부재는 401 (주문 조회도 하지 않음)")
    void confirm_noUser_unauthorized() {
        assertThatThrownBy(() -> purchaseConfirmService.confirm(1L, null))
                .isInstanceOf(UnauthorizedException.class);
        then(orderRepository).should(never()).findById(anyLong());
    }

    // ── 자동 구매확정 ──────────────────────────────────────────────

    @Test
    @DisplayName("자동확정 — 기준일 경과 대상만 조건부 UPDATE 후 알림 생성")
    void autoConfirmExpired_confirmsTargets() {
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(new AutoConfirmTarget(1L, 10L), new AutoConfirmTarget(2L, 20L)));
        given(orderRepository.confirmPurchaseIfEligible(anyLong(), eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(1);

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isEqualTo(2);
        then(notificationService).should(times(1))
                .create(10L, NotificationType.PURCHASE_CONFIRMED, 1L);
        then(notificationService).should(times(1))
                .create(20L, NotificationType.PURCHASE_CONFIRMED, 2L);
    }

    @Test
    @DisplayName("자동확정 — 조회 기준 시각은 현재로부터 기준일(7일) 이전")
    void autoConfirmExpired_usesConfiguredThreshold() {
        LocalDateTime before = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of());

        purchaseConfirmService.autoConfirmExpired();

        LocalDateTime after = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(orderRepository).should().findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("자동확정 — 미경과·이미확정·미배송 주문은 조회 대상이 아니라 확정되지 않는다")
    void autoConfirmExpired_noTargets() {
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of());

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isZero();
        then(orderRepository).should(never()).confirmPurchaseIfEligible(anyLong(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class));
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("자동확정 — 다른 인스턴스가 선점(갱신 0건)하면 알림을 중복 발송하지 않는다")
    void autoConfirmExpired_lostRace_skipsNotification() {
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(new AutoConfirmTarget(1L, 10L)));
        given(orderRepository.confirmPurchaseIfEligible(anyLong(), eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(0);

        int confirmed = purchaseConfirmService.autoConfirmExpired();

        assertThat(confirmed).isZero();
        then(notificationService).should(never()).create(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("자동확정 — 배치 크기만큼만 조회해 대량 적체 시 트랜잭션을 제한한다")
    void autoConfirmExpired_limitsBatchSize() {
        given(orderRepository.findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of());

        purchaseConfirmService.autoConfirmExpired();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(orderRepository).should().findAutoConfirmTargets(eq(DeliveryStatus.DELIVERED),
                any(LocalDateTime.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
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

    /** 배송완료(DELIVERED) 주문 — 구매확정 자격 충족 상태 */
    private Order deliveredOrder() {
        Order order = confirmedOrder();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        return order;
    }
}
