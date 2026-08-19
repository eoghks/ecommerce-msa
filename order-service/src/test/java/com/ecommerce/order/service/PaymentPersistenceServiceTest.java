package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PaymentAlreadyApprovedException;
import com.ecommerce.order.exception.PaymentAmountMismatchException;
import com.ecommerce.order.exception.PaymentInProgressException;
import com.ecommerce.order.exception.PaymentNotCompletedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPersistenceService 단위 테스트 (V1.1-6)")
class PaymentPersistenceServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID  = 7L;
    private static final Long AMOUNT   = 20_000L;
    private static final String PAYMENT_KEY = "test_payment_key_123456";
    private static final String PG_ORDER_ID = "ORD-00000001-a1b2c3d4";

    @InjectMocks private PaymentPersistenceService paymentPersistenceService;

    @Mock private PaymentRepository         paymentRepository;
    @Mock private OrderRepository           orderRepository;
    @Mock private NotificationService       notificationService;
    @Mock private PaymentIncidentService    paymentIncidentService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    // ── 승인 준비(prepare) ─────────────────────────────────────────

    @Test
    @DisplayName("승인 준비 — 소유자·금액·멱등 검증을 통과하면 READY 결제를 만든다(PG 주문번호 저장)")
    void prepare_success() {
        givenLockedOrder(unpaidOrder());
        givenNoPreviousPayment();
        given(paymentRepository.save(any(Payment.class))).willAnswer(call -> {
            Payment payment = call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 99L);
            return payment;
        });

        Long paymentId = paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID));

        assertThat(paymentId).isEqualTo(99L);
    }

    @Test
    @DisplayName("M-03 승인 준비 — 주문 행을 잠그고 읽어 동시 confirm 이 함께 통과하지 못하게 한다")
    void prepare_locksOrderRow() {
        givenLockedOrder(unpaidOrder());
        givenNoPreviousPayment();
        given(paymentRepository.save(any(Payment.class))).willAnswer(call -> {
            Payment payment = call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 99L);
            return payment;
        });

        paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID));

        then(orderRepository).should(times(1)).findByIdForUpdate(ORDER_ID);
        then(orderRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("M-03 승인 준비 — 진행 중(READY) 결제가 있으면 409 (이중 출금 차단)")
    void prepare_inFlightPayment_conflict() {
        givenLockedOrder(unpaidOrder());
        given(paymentRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection()))
                .willReturn(false);
        given(paymentRepository.findByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection()))
                .willReturn(List.of(readyPayment()));

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(PaymentInProgressException.class);
        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("M-05 승인 준비 — 다른 주문의 PG 주문번호는 거부한다")
    void prepare_foreignPgOrderId_rejected() {
        givenLockedOrder(unpaidOrder());
        givenNoPreviousPayment();

        assertThatThrownBy(() ->
                paymentPersistenceService.prepare(USER_ID, request(AMOUNT, "ORD-00000999-a1b2c3d4")))
                .isInstanceOf(IllegalArgumentException.class);
        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("승인 준비 — 요청 금액이 서버 결제금액과 다르면 거부(위변조 차단)")
    void prepare_amountMismatch_rejected() {
        givenLockedOrder(unpaidOrder());
        givenNoPreviousPayment();

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(10L, PG_ORDER_ID)))
                .isInstanceOf(PaymentAmountMismatchException.class);
        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("승인 준비 — 이미 승인된 주문 재승인 시도는 409")
    void prepare_alreadyApprovedPayment_conflict() {
        givenLockedOrder(unpaidOrder());
        given(paymentRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection())).willReturn(true);

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(PaymentAlreadyApprovedException.class);
    }

    @Test
    @DisplayName("승인 준비 — 이미 결제 승인 단계를 지난 주문(PENDING)은 409")
    void prepare_orderAlreadyPaid_conflict() {
        Order order = unpaidOrder();
        order.markPaid();
        givenLockedOrder(order);

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(PaymentAlreadyApprovedException.class);
    }

    @Test
    @DisplayName("승인 준비 — 동일 PG 거래키 재사용은 409 (멱등)")
    void prepare_duplicatePaymentKey_conflict() {
        givenLockedOrder(unpaidOrder());
        given(paymentRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection())).willReturn(false);
        given(paymentRepository.findByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection()))
                .willReturn(List.of());
        given(paymentRepository.findByPgPaymentKey(PAYMENT_KEY))
                .willReturn(Optional.of(readyPayment()));

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(PaymentAlreadyApprovedException.class);
    }

    @Test
    @DisplayName("승인 준비 — 타인 주문 승인 시도는 404 (정보 노출 방지)")
    void prepare_otherUsersOrder_notFound() {
        givenLockedOrder(unpaidOrder());

        assertThatThrownBy(() -> paymentPersistenceService.prepare(999L, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(OrderNotFoundException.class);
        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("승인 준비 — 취소된 주문은 결제할 수 없다(409 상태 충돌)")
    void prepare_cancelledOrder_illegalState() {
        Order order = unpaidOrder();
        order.cancel();
        givenLockedOrder(order);

        assertThatThrownBy(() -> paymentPersistenceService.prepare(USER_ID, request(AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── 승인 확정(markApproved) ────────────────────────────────────

    @Test
    @DisplayName("승인 확정 — 결제 APPROVED + 주문 PENDING 전이 + order.created 발행")
    void markApproved_transitionsOrderAndPublishesEvent() {
        Payment payment = readyPayment();
        Order order = unpaidOrder();
        given(paymentRepository.findById(99L)).willReturn(Optional.of(payment));
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentPersistenceService.markApproved(99L, tossResult("DONE", AMOUNT, PG_ORDER_ID));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        // 승인 이후에만 재고 차감 Saga 가 시작된다 (§5)
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderCreatedApplicationEvent.class));
    }

    @Test
    @DisplayName("H-03 승인 확정 — 가상계좌 입금대기(WAITING_FOR_DEPOSIT)는 확정하지 않는다")
    void markApproved_waitingForDeposit_rejected() {
        Payment payment = readyPayment();
        given(paymentRepository.findById(99L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentPersistenceService.markApproved(
                99L, tossResult("WAITING_FOR_DEPOSIT", AMOUNT, PG_ORDER_ID)))
                .isInstanceOf(PaymentNotCompletedException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("M-01 승인 확정 — 응답 금액이 승인 금액과 다르면 확정하지 않는다")
    void markApproved_amountMismatch_rejected() {
        given(paymentRepository.findById(99L)).willReturn(Optional.of(readyPayment()));

        assertThatThrownBy(() -> paymentPersistenceService.markApproved(
                99L, tossResult("DONE", 1_000L, PG_ORDER_ID)))
                .isInstanceOf(PaymentNotCompletedException.class);
    }

    @Test
    @DisplayName("M-01 승인 확정 — 응답 PG 주문번호가 저장값과 다르면 확정하지 않는다")
    void markApproved_pgOrderIdMismatch_rejected() {
        given(paymentRepository.findById(99L)).willReturn(Optional.of(readyPayment()));

        assertThatThrownBy(() -> paymentPersistenceService.markApproved(
                99L, tossResult("DONE", AMOUNT, "ORD-00000002-zzzz")))
                .isInstanceOf(PaymentNotCompletedException.class);
    }

    // ── 승인 실패·미확정·정리 ──────────────────────────────────────

    @Test
    @DisplayName("승인 실패 — 결제 FAILED + 주문 CANCELLED + 사유 기록 (§5.1)")
    void markFailed_cancelsOrder() {
        Payment payment = readyPayment();
        Order order = unpaidOrder();
        given(paymentRepository.findById(99L)).willReturn(Optional.of(payment));
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentPersistenceService.markFailed(99L, "카드 한도 초과");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).isEqualTo("카드 한도 초과");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(notificationService).should()
                .create(eq(USER_ID), eq(NotificationType.ORDER_CANCELLED), eq(ORDER_ID));
    }

    @Test
    @DisplayName("C-01 결과 미확정 — 결제 UNKNOWN 유지 + 미결 기록, 주문은 취소하지 않는다")
    void markUnknown_keepsOrderAndRecordsIncident() {
        Payment payment = readyPayment();
        given(paymentRepository.findById(99L)).willReturn(Optional.of(payment));

        paymentPersistenceService.markUnknown(99L, PAYMENT_KEY, "재조회 판정 불가");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
        then(paymentIncidentService).should(times(1))
                .record(ORDER_ID, USER_ID, "재조회 판정 불가");
        then(orderRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("C-02 고아 결제 정리 — 결제만 FAILED 로 닫고 주문은 결제 대기로 남겨 재결제를 허용한다")
    void abandon_closesPaymentOnly() {
        Payment payment = readyPayment();
        given(paymentRepository.findById(99L)).willReturn(Optional.of(payment));

        paymentPersistenceService.abandon(99L, "결제 미완료(진행 중 결제 정리)");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        then(orderRepository).should(never()).findById(anyLong());
        then(notificationService).shouldHaveNoInteractions();
    }

    // ── helper ───────────────────────────────────────────────────

    private void givenLockedOrder(Order order) {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
    }

    private void givenNoPreviousPayment() {
        given(paymentRepository.existsByOrderIdAndStatusIn(anyLong(), anyCollection())).willReturn(false);
        given(paymentRepository.findByOrderIdAndStatusIn(anyLong(), anyCollection()))
                .willReturn(List.of());
        given(paymentRepository.findByPgPaymentKey(anyString())).willReturn(Optional.empty());
    }

    private PaymentConfirmRequest request(Long amount, String pgOrderId) {
        return new PaymentConfirmRequest(PAYMENT_KEY, pgOrderId, ORDER_ID, amount);
    }

    private Order unpaidOrder() {
        OrderItem item = OrderItem.builder()
                .productId(10L).productName("상품").price(AMOUNT).quantity(1).sellerId(3L).build();
        Order order = Order.builder().userId(USER_ID).totalPrice(AMOUNT).items(List.of(item)).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private Payment readyPayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId(PG_ORDER_ID).amount(AMOUNT).build();
        ReflectionTestUtils.setField(payment, "id", 99L);
        return payment;
    }

    private TossPaymentClient.TossPayment tossResult(String status, Long totalAmount, String pgOrderId) {
        return new TossPaymentClient.TossPayment(
                PAYMENT_KEY, pgOrderId, status, totalAmount, totalAmount, OffsetDateTime.now());
    }
}
