package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
@DisplayName("PaymentService 단위 테스트 (V1.1-6)")
class PaymentServiceTest {

    private static final Long ORDER_ID   = 1L;
    private static final Long USER_ID    = 7L;
    private static final Long AMOUNT     = 20_000L;
    private static final Long PAYMENT_ID = 99L;
    private static final String PAYMENT_KEY = "test_payment_key_123456";

    /** PG 주문번호 규칙(PgOrderId) — 프론트와 동일 포맷이어야 승인이 성립한다 */
    private static final String PG_ORDER_ID = "ORD-00000001";

    @InjectMocks private PaymentService paymentService;

    @Mock private PaymentPersistenceService paymentPersistenceService;
    @Mock private TossPaymentClient         tossPaymentClient;
    @Mock private PaymentRepository         paymentRepository;
    @Mock private OrderRepository           orderRepository;

    // ── 승인 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("승인 — 준비 후 PG 승인을 호출하고 승인 결과를 확정한다")
    void confirm_success() {
        given(paymentPersistenceService.prepare(eq(USER_ID), any(PaymentConfirmRequest.class)))
                .willReturn(PAYMENT_ID);
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).willReturn(tossResult());
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        PaymentResponse response = paymentService.confirm(USER_ID, request());

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        then(tossPaymentClient).should(times(1)).confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("승인 — PG 승인 실패 시 결제 실패·주문 취소 보상 후 예외를 그대로 전달 (§5.1)")
    void confirm_pgFailure_compensates() {
        given(paymentPersistenceService.prepare(eq(USER_ID), any(PaymentConfirmRequest.class)))
                .willReturn(PAYMENT_ID);
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
                .willThrow(new PaymentApprovalFailedException("카드 한도 초과"));

        assertThatThrownBy(() -> paymentService.confirm(USER_ID, request()))
                .isInstanceOf(PaymentApprovalFailedException.class)
                .hasMessage("카드 한도 초과");

        then(paymentPersistenceService).should(times(1)).markFailed(PAYMENT_ID, "카드 한도 초과");
        then(paymentPersistenceService).should(never()).markApproved(anyLong(), any());
    }

    @Test
    @DisplayName("승인 — 인증 정보(X-User-Id) 없으면 401, PG 호출 없음")
    void confirm_noUser_unauthorized() {
        assertThatThrownBy(() -> paymentService.confirm(null, request()))
                .isInstanceOf(UnauthorizedException.class);
        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("조회 — 본인 결제만 반환한다")
    void getByOrder_success() {
        given(paymentRepository.findActiveByOrderId(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.of(approvedPayment()));

        PaymentResponse response = paymentService.getByOrder(ORDER_ID, USER_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("조회 — 타인 결제는 404 (정보 노출 방지)")
    void getByOrder_otherUser_notFound() {
        given(paymentRepository.findActiveByOrderId(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.of(approvedPayment()));

        assertThatThrownBy(() -> paymentService.getByOrder(ORDER_ID, 999L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ── 만료 (§11.1) ─────────────────────────────────────────────

    @Test
    @DisplayName("만료 — 정책 시간이 지난 결제 대기 주문을 조건부 UPDATE 로 취소한다")
    void expireUnpaidOrders_cancelsExpiredTargets() {
        ReflectionTestUtils.setField(paymentService, "paymentExpireMinutes", 30);
        given(orderRepository.expirePaymentPendingOrders(
                eq(OrderStatus.PAYMENT_PENDING), eq(OrderStatus.CANCELLED), any(), any()))
                .willReturn(2);

        int expired = paymentService.expireUnpaidOrders();

        assertThat(expired).isEqualTo(2);
    }

    @Test
    @DisplayName("만료 — 기준 시각은 현재시각 - 정책 분(30분)으로 계산한다")
    void expireUnpaidOrders_usesPolicyThreshold() {
        ReflectionTestUtils.setField(paymentService, "paymentExpireMinutes", 30);
        given(orderRepository.expirePaymentPendingOrders(any(), any(), any(), any())).willReturn(0);

        LocalDateTime before = LocalDateTime.now();
        paymentService.expireUnpaidOrders();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        then(orderRepository).should().expirePaymentPendingOrders(
                eq(OrderStatus.PAYMENT_PENDING), eq(OrderStatus.CANCELLED),
                threshold.capture(), now.capture());
        assertThat(threshold.getValue()).isBeforeOrEqualTo(before.minusMinutes(30));
        assertThat(now.getValue()).isAfterOrEqualTo(before);
    }

    // ── helper ───────────────────────────────────────────────────

    private PaymentConfirmRequest request() {
        return new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT);
    }

    private TossPaymentClient.TossPayment tossResult() {
        return new TossPaymentClient.TossPayment(
                PAYMENT_KEY, PG_ORDER_ID, "DONE", AMOUNT, AMOUNT, OffsetDateTime.now());
    }

    private Payment approvedPayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS).amount(AMOUNT).build();
        payment.approve(PAYMENT_KEY, LocalDateTime.now());
        return payment;
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(PAYMENT_ID, ORDER_ID, PaymentProvider.TOSS, status,
                AMOUNT, 0L, LocalDateTime.now(), null, null);
    }
}
