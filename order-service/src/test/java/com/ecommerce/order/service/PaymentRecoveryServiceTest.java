package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.exception.PaymentCancelFailedException;
import com.ecommerce.order.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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

/**
 * 결제 결과 미확정 복구 단위 테스트 (C-01, C-02).
 * 실제 토스 API 는 호출하지 않고 클라이언트를 mock 으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRecoveryService 단위 테스트 (C-01/C-02)")
class PaymentRecoveryServiceTest {

    private static final Long ORDER_ID   = 1L;
    private static final Long USER_ID    = 7L;
    private static final Long PAYMENT_ID = 99L;
    private static final Long AMOUNT     = 20_000L;
    private static final String PAYMENT_KEY = "test_payment_key_123456";
    private static final String PG_ORDER_ID = "ORD-00000001-a1b2c3d4";

    @InjectMocks private PaymentRecoveryService paymentRecoveryService;

    @Mock private PaymentPersistenceService paymentPersistenceService;
    @Mock private TossPaymentClient         tossPaymentClient;
    @Mock private PaymentRepository         paymentRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentRecoveryService, "inFlightStaleMinutes", 3);
        ReflectionTestUtils.setField(paymentRecoveryService, "reconcileBatchSize", 100);
    }

    // ── C-01 통신 실패 후 재조회 ──────────────────────────────────

    @Test
    @DisplayName("C-01 재조회에서 승인 확인 — 정상 확정 경로로 진행한다(돈 유실 방지)")
    void resolveUnconfirmed_approvedAtPg_confirms() {
        givenPayment(readyPayment());
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willReturn(Optional.of(tossResult("DONE")));
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        PaymentResponse response = paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), anyString());
        then(paymentPersistenceService).should(never()).markUnknown(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("C-01 재조회에서 접수 없음(404) — 출금이 없으므로 실패 확정 + 주문 취소 보상")
    void resolveUnconfirmed_notFoundAtPg_marksFailed() {
        givenPayment(readyPayment());
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        then(paymentPersistenceService).should(times(1)).markFailed(eq(PAYMENT_ID), anyString());
    }

    @Test
    @DisplayName("C-01 재조회에서 중단(ABORTED) — 실패로 확정한다")
    void resolveUnconfirmed_terminatedAtPg_marksFailed() {
        givenPayment(readyPayment());
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willReturn(Optional.of(tossResult("ABORTED")));

        assertThatThrownBy(() -> paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        then(paymentPersistenceService).should(times(1)).markFailed(eq(PAYMENT_ID), anyString());
    }

    @Test
    @DisplayName("C-01 재조회가 진행 중 상태 — 판정 불가이므로 UNKNOWN 으로 남긴다(환불 경로 유지)")
    void resolveUnconfirmed_inProgressAtPg_marksUnknown() {
        givenPayment(readyPayment());
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willReturn(Optional.of(tossResult("IN_PROGRESS")));

        assertThatThrownBy(() -> paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        then(paymentPersistenceService).should(times(1))
                .markUnknown(eq(PAYMENT_ID), eq(PAYMENT_KEY), anyString());
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("C-01 재조회 자체가 실패 — 실패로 단정하지 않고 UNKNOWN 으로 남긴다")
    void resolveUnconfirmed_lookupFails_marksUnknown() {
        givenPayment(readyPayment());
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willThrow(new PaymentGatewayUnavailableException("결과 미확정"));

        assertThatThrownBy(() -> paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        then(paymentPersistenceService).should(times(1)).markUnknown(eq(PAYMENT_ID), any(), anyString());
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), anyString());
    }

    // ── C-02 승인 후 확정 실패 보상 ───────────────────────────────

    @Test
    @DisplayName("C-02 확정 실패(만료 경쟁 등) — PG 취소로 보상하고 결제를 실패로 닫는다")
    void confirmApproved_persistFails_compensatesByPgCancel() {
        TossPaymentClient.TossPayment approved = tossResult("DONE");
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willThrow(new IllegalStateException("결제 완료 처리할 수 없는 주문 상태입니다."));

        assertThatThrownBy(() ->
                paymentRecoveryService.confirmApproved(PAYMENT_ID, AMOUNT, approved))
                .isInstanceOf(IllegalStateException.class);

        then(tossPaymentClient).should(times(1))
                .cancel(eq(PAYMENT_KEY), anyString(), eq(AMOUNT), eq("PAYCMP-99"));
        then(paymentPersistenceService).should(times(1)).markFailed(eq(PAYMENT_ID), anyString());
    }

    @Test
    @DisplayName("C-02 보상 취소까지 실패 — UNKNOWN 으로 남겨 수동 환불 대상으로 만든다")
    void confirmApproved_compensationFails_marksUnknown() {
        TossPaymentClient.TossPayment approved = tossResult("DONE");
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willThrow(new IllegalStateException("확정 실패"));
        given(tossPaymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
                .willThrow(new PaymentCancelFailedException("PG 취소 실패"));

        assertThatThrownBy(() ->
                paymentRecoveryService.confirmApproved(PAYMENT_ID, AMOUNT, approved))
                .isInstanceOf(IllegalStateException.class);

        then(paymentPersistenceService).should(times(1))
                .markUnknown(eq(PAYMENT_ID), eq(PAYMENT_KEY), anyString());
    }

    @Test
    @DisplayName("확정 성공 — 보상 취소를 호출하지 않는다")
    void confirmApproved_success_noCompensation() {
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        paymentRecoveryService.confirmApproved(PAYMENT_ID, AMOUNT, tossResult("DONE"));

        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    // ── C-02 고아 결제 회수 ───────────────────────────────────────

    @Test
    @DisplayName("C-02 재결제 차단 해소 — 미승인이 확인된 고아 결제는 결제만 닫아 재결제를 허용한다")
    void releaseStalePayments_unapproved_abandons() {
        given(paymentRepository.findStaleByOrderId(eq(ORDER_ID), eq(USER_ID), anyCollection(), any()))
                .willReturn(List.of(readyPayment()));
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID)).willReturn(Optional.empty());

        paymentRecoveryService.releaseStalePayments(ORDER_ID, USER_ID);

        then(paymentPersistenceService).should(times(1)).abandon(eq(PAYMENT_ID), anyString());
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("C-02 재결제 차단 해소 — 대상이 없으면 PG 를 호출하지 않는다")
    void releaseStalePayments_noTarget_noPgCall() {
        given(paymentRepository.findStaleByOrderId(eq(ORDER_ID), eq(USER_ID), anyCollection(), any()))
                .willReturn(List.of());

        paymentRecoveryService.releaseStalePayments(ORDER_ID, USER_ID);

        then(tossPaymentClient).shouldHaveNoInteractions();
        then(paymentPersistenceService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("C-01 회수 스케줄 — 승인 확인된 고아 결제는 확정하고 건수를 돌려준다")
    void reconcileInFlightPayments_approved_confirms() {
        given(paymentRepository.findStalePayments(anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of(readyPayment()));
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willReturn(Optional.of(tossResult("DONE")));
        given(paymentPersistenceService.markApproved(eq(PAYMENT_ID), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        assertThat(paymentRecoveryService.reconcileInFlightPayments()).isEqualTo(1);
    }

    @Test
    @DisplayName("회수 스케줄 — 한 건이 실패해도 배치 전체가 멈추지 않는다")
    void reconcileInFlightPayments_isolatesFailure() {
        given(paymentRepository.findStalePayments(anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of(readyPayment()));
        given(tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .willThrow(new PaymentGatewayUnavailableException("판정 불가"));

        assertThat(paymentRecoveryService.reconcileInFlightPayments()).isZero();
        then(paymentPersistenceService).should(times(1)).markUnknown(eq(PAYMENT_ID), any(), anyString());
    }

    @Test
    @DisplayName("회수 스케줄 — PG 주문번호가 없는 결제는 재조회하지 않고 미결로 남긴다")
    void reconcileInFlightPayments_withoutPgOrderId_marksUnknown() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS).amount(AMOUNT).build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        given(paymentRepository.findStalePayments(anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of(payment));

        assertThat(paymentRecoveryService.reconcileInFlightPayments()).isZero();
        then(tossPaymentClient).shouldHaveNoInteractions();
        then(paymentPersistenceService).should(times(1)).markUnknown(eq(PAYMENT_ID), any(), anyString());
    }

    // ── helper ───────────────────────────────────────────────────

    private void givenPayment(Payment payment) {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
    }

    private Payment readyPayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId(PG_ORDER_ID).amount(AMOUNT).build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    private TossPaymentClient.TossPayment tossResult(String status) {
        return new TossPaymentClient.TossPayment(
                PAYMENT_KEY, PG_ORDER_ID, status, AMOUNT, AMOUNT, OffsetDateTime.now());
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(PAYMENT_ID, ORDER_ID, PaymentProvider.TOSS, status,
                AMOUNT, 0L, LocalDateTime.now(), null, null);
    }
}
