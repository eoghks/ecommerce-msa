package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.exception.PaymentCancelFailedException;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCancelService 단위 테스트 (V1.1-6)")
class PaymentCancelServiceTest {

    private static final Long ORDER_ID   = 1L;
    private static final Long USER_ID    = 7L;
    private static final Long PAYMENT_ID = 99L;
    private static final Long AMOUNT     = 20_000L;
    private static final String PAYMENT_KEY = "test_payment_key_123456";
    private static final String REASON = "재고 확보 실패(자동취소)";

    /** H-01: 멱등키 = PAYCNL-{결제 id}-{취소 직전 누적 취소액} */
    private static final String FIRST_CANCEL_KEY = "PAYCNL-99-0";

    @InjectMocks private PaymentCancelService paymentCancelService;

    @Mock private PaymentRepository      paymentRepository;
    @Mock private PaymentIncidentService paymentIncidentService;
    @Mock private TossPaymentClient      tossPaymentClient;

    @Test
    @DisplayName("전액 환불 — 승인된 결제에 PG 취소를 호출하고 CANCELED 로 전이한다 (§5.1)")
    void cancelForOrder_fullRefund() {
        Payment payment = approvedPayment();
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(times(1))
                .cancel(PAYMENT_KEY, REASON, AMOUNT, FIRST_CANCEL_KEY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelledAmount()).isEqualTo(AMOUNT);
        assertThat(payment.getCanceledAt()).isNotNull();
    }

    @Test
    @DisplayName("H-02 환불 — 잔여 취소가능액 계산이 겹치지 않게 결제 행을 잠그고 읽는다")
    void cancelForOrder_locksPaymentRow() {
        givenLockedPayment(approvedPayment());

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(paymentRepository).should(times(1))
                .findActiveByOrderIdForUpdate(ORDER_ID, PaymentStatus.FAILED);
        then(paymentRepository).should(never()).findActiveByOrderId(anyLong(), any());
    }

    @Test
    @DisplayName("부분 환불 — 요청 금액만 취소하고 승인 상태를 유지한다 (§4.2)")
    void cancelForOrder_partialRefund() {
        Payment payment = approvedPayment();
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, 5_000L, "반품 승인");

        then(tossPaymentClient).should(times(1))
                .cancel(PAYMENT_KEY, "반품 승인", 5_000L, FIRST_CANCEL_KEY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.cancellableAmount()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("H-01 부분 환불 — 멱등키에 취소 회차(누적 취소액)가 반영돼 회차별로 달라진다")
    void cancelForOrder_idempotencyKeyIncludesCancelRound() {
        Payment payment = approvedPayment();
        payment.applyCancel(5_000L, LocalDateTime.now());
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, 5_000L, "반품 승인");

        then(tossPaymentClient).should(times(1))
                .cancel(PAYMENT_KEY, "반품 승인", 5_000L, "PAYCNL-99-5000");
    }

    @Test
    @DisplayName("멱등 — 이미 전액 취소된 결제는 PG 재호출 없이 건너뛴다")
    void cancelForOrder_alreadyCancelled_skips() {
        Payment payment = approvedPayment();
        payment.applyCancel(AMOUNT, LocalDateTime.now());
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(never()).cancel(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("승인된 결제가 없으면 no-op — 보상 경로에서 무조건 호출해도 안전하다")
    void cancelForOrder_noPayment_noOp() {
        given(paymentRepository.findActiveByOrderIdForUpdate(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.empty());

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("승인 전(READY) 결제는 취소하지 않는다")
    void cancelForOrder_readyPayment_skips() {
        givenLockedPayment(readyPayment());

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("M-06 0원 결제(§11-3) — PG 왕복 없이 CANCELED 로 전이한다(APPROVED 잔존 방지)")
    void cancelForOrder_zeroAmountPayment_transitionsToCancelled() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.NONE).amount(0L).build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.approve(null, LocalDateTime.now());
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, 0L, REASON);

        then(tossPaymentClient).should(never()).cancel(any(), any(), anyLong(), any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isNotNull();
    }

    @Test
    @DisplayName("요청 금액이 잔여 취소가능액을 넘으면 잔여액까지만 취소한다(과환불 방지)")
    void cancelForOrder_capsAtRemainingAmount() {
        Payment payment = approvedPayment();
        payment.applyCancel(15_000L, LocalDateTime.now());
        givenLockedPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(times(1))
                .cancel(PAYMENT_KEY, REASON, 5_000L, "PAYCNL-99-15000");
        assertThat(payment.getCancelledAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("H-04 환불 실패 — 별도 트랜잭션으로 미결 기록을 남긴 뒤 예외를 그대로 올린다")
    void cancelForOrder_pgFailure_recordsIncidentAndRethrows() {
        Payment payment = approvedPayment();
        givenLockedPayment(payment);
        given(tossPaymentClient.cancel(eq(PAYMENT_KEY), eq(REASON), eq(AMOUNT), anyString()))
                .willThrow(new PaymentCancelFailedException("이미 취소된 결제"));

        assertThatThrownBy(() -> paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON))
                .isInstanceOf(PaymentCancelFailedException.class);

        then(paymentIncidentService).should(times(1))
                .record(eq(ORDER_ID), eq(USER_ID), anyString());
        // 환불이 안 됐으므로 취소 금액을 반영하지 않는다
        assertThat(payment.getCancelledAmount()).isZero();
    }

    private void givenLockedPayment(Payment payment) {
        given(paymentRepository.findActiveByOrderIdForUpdate(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.of(payment));
    }

    private Payment approvedPayment() {
        Payment payment = readyPayment();
        payment.approve(PAYMENT_KEY, LocalDateTime.now());
        return payment;
    }

    private Payment readyPayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId("ORD-00000001-a1b2c3d4").amount(AMOUNT).build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }
}
