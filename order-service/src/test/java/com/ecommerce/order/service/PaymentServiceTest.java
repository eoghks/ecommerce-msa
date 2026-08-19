package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.FailedOrderLog;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.PaymentExpireTarget;
import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.exception.PaymentNotCompletedException;
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.FailedOrderLogRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
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
import java.time.OffsetDateTime;
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

    /** PG 주문번호 — M-05: 시도마다 접미사가 달라지므로 클라이언트 값을 그대로 쓴다 */
    private static final String PG_ORDER_ID = "ORD-00000001-a1b2c3d4";

    @InjectMocks private PaymentService paymentService;

    @Mock private PaymentPersistenceService paymentPersistenceService;
    @Mock private PaymentRecoveryService    paymentRecoveryService;
    @Mock private TossPaymentClient         tossPaymentClient;
    @Mock private PaymentRepository         paymentRepository;
    @Mock private OrderRepository           orderRepository;
    @Mock private FailedOrderLogRepository  failedOrderLogRepository;

    // ── 승인 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("승인 — 준비 후 PG 승인을 호출하고 승인 결과를 확정한다")
    void confirm_success() {
        givenPrepared();
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).willReturn(tossResult("DONE"));
        given(paymentRecoveryService.confirmApproved(eq(PAYMENT_ID), eq(AMOUNT), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        PaymentResponse response = paymentService.confirm(USER_ID, request());

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        then(tossPaymentClient).should(times(1)).confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("C-02 승인 — 준비 전에 고아 진행 중 결제를 회수해 재결제가 막히지 않게 한다")
    void confirm_releasesStalePaymentsBeforePrepare() {
        givenPrepared();
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).willReturn(tossResult("DONE"));
        given(paymentRecoveryService.confirmApproved(anyLong(), anyLong(), any()))
                .willReturn(response(PaymentStatus.APPROVED));

        paymentService.confirm(USER_ID, request());

        then(paymentRecoveryService).should(times(1)).releaseStalePayments(ORDER_ID, USER_ID);
    }

    @Test
    @DisplayName("승인 — PG 가 명시적으로 거절하면 결제 실패·주문 취소 보상 후 예외를 그대로 전달 (§5.1)")
    void confirm_pgRejection_compensates() {
        givenPrepared();
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
                .willThrow(new PaymentApprovalFailedException("카드 한도 초과"));

        assertThatThrownBy(() -> paymentService.confirm(USER_ID, request()))
                .isInstanceOf(PaymentApprovalFailedException.class)
                .hasMessage("카드 한도 초과");

        then(paymentPersistenceService).should(times(1)).markFailed(PAYMENT_ID, "카드 한도 초과");
        then(paymentRecoveryService).should(never()).confirmApproved(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("C-01 승인 — PG 통신 실패는 실패로 단정하지 않고 재조회로 판정한다(돈 유실 방지)")
    void confirm_communicationFailure_resolvesByLookup() {
        givenPrepared();
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
                .willThrow(new PaymentGatewayUnavailableException("결제 결과를 확인하지 못했습니다."));
        given(paymentRecoveryService.resolveUnconfirmed(PAYMENT_ID))
                .willReturn(response(PaymentStatus.APPROVED));

        PaymentResponse response = paymentService.confirm(USER_ID, request());

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        then(paymentRecoveryService).should(times(1)).resolveUnconfirmed(PAYMENT_ID);
        // 통신 실패만으로 결제 FAILED·주문 CANCELLED 로 확정하지 않는다
        then(paymentPersistenceService).should(never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("M-01 승인 — 요청과 다른 거래키 응답은 확정하지 않고 승인분을 보상 취소한다")
    void confirm_paymentKeyMismatch_compensatesAndRejects() {
        givenPrepared();
        TossPaymentClient.TossPayment other = new TossPaymentClient.TossPayment(
                "other_payment_key", PG_ORDER_ID, "DONE", AMOUNT, AMOUNT, OffsetDateTime.now());
        given(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).willReturn(other);

        assertThatThrownBy(() -> paymentService.confirm(USER_ID, request()))
                .isInstanceOf(PaymentNotCompletedException.class);

        then(paymentRecoveryService).should(times(1))
                .compensateApproval(eq(PAYMENT_ID), eq(AMOUNT), eq(other));
        then(paymentRecoveryService).should(never()).confirmApproved(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("승인 — 인증 정보(X-User-Id) 없으면 401, PG 호출 없음")
    void confirm_noUser_unauthorized() {
        assertThatThrownBy(() -> paymentService.confirm(null, request()))
                .isInstanceOf(UnauthorizedException.class);
        then(tossPaymentClient).shouldHaveNoInteractions();
        then(paymentRecoveryService).shouldHaveNoInteractions();
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
    @DisplayName("만료 — 대상 주문을 건별 조건부 UPDATE 로 취소하고 사유를 대장에 남긴다 (M-07)")
    void expireUnpaidOrders_cancelsAndRecordsReason() {
        givenExpirePolicy();
        given(orderRepository.findExpirableOrders(eq(OrderStatus.PAYMENT_PENDING), anyCollection(),
                any(), any(Pageable.class)))
                .willReturn(List.of(new PaymentExpireTarget(ORDER_ID, USER_ID),
                        new PaymentExpireTarget(2L, USER_ID)));
        given(orderRepository.expireIfStillUnpaid(anyLong(), eq(OrderStatus.PAYMENT_PENDING),
                eq(OrderStatus.CANCELLED), anyCollection(), any(), any()))
                .willReturn(1);

        int expired = paymentService.expireUnpaidOrders();

        assertThat(expired).isEqualTo(2);
        then(failedOrderLogRepository).should(times(2)).save(any(FailedOrderLog.class));
    }

    @Test
    @DisplayName("만료 — 조건부 UPDATE 가 0건이면(선점·결제 시작) 취소로 세지 않고 사유도 남기지 않는다")
    void expireUnpaidOrders_skipsWhenUpdateMisses() {
        givenExpirePolicy();
        given(orderRepository.findExpirableOrders(any(), anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of(new PaymentExpireTarget(ORDER_ID, USER_ID)));
        given(orderRepository.expireIfStillUnpaid(anyLong(), any(), any(), anyCollection(), any(), any()))
                .willReturn(0);

        assertThat(paymentService.expireUnpaidOrders()).isZero();
        then(failedOrderLogRepository).should(never()).save(any(FailedOrderLog.class));
    }

    @Test
    @DisplayName("C-02 만료 — 진행 중(READY)·미확정(UNKNOWN) 결제를 배제 조건으로 넘긴다")
    void expireUnpaidOrders_excludesInFlightPayments() {
        givenExpirePolicy();
        given(orderRepository.findExpirableOrders(any(), anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of());

        paymentService.expireUnpaidOrders();

        ArgumentCaptor<java.util.Collection<PaymentStatus>> statuses =
                ArgumentCaptor.forClass(java.util.Collection.class);
        then(orderRepository).should().findExpirableOrders(eq(OrderStatus.PAYMENT_PENDING),
                statuses.capture(), any(), any(Pageable.class));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(PaymentStatus.READY, PaymentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("만료 — 기준 시각은 현재시각 - 정책 분(30분)으로 계산한다")
    void expireUnpaidOrders_usesPolicyThreshold() {
        givenExpirePolicy();
        given(orderRepository.findExpirableOrders(any(), anyCollection(), any(), any(Pageable.class)))
                .willReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        paymentService.expireUnpaidOrders();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        then(orderRepository).should().findExpirableOrders(any(), anyCollection(),
                threshold.capture(), any(Pageable.class));
        // 서비스 내부 now() 는 before~after 사이에 잡히므로 기준 시각도 그 구간에서 30분을 뺀 값이어야 한다.
        // (단일 시점과 비교하면 실행 중 흐른 시간 때문에 CI 에서 깨지는 flaky 단언이 된다)
        assertThat(threshold.getValue())
                .isAfterOrEqualTo(before.minusMinutes(30))
                .isBeforeOrEqualTo(after.minusMinutes(30));
    }

    // ── helper ───────────────────────────────────────────────────

    private void givenPrepared() {
        given(paymentPersistenceService.prepare(eq(USER_ID), any(PaymentConfirmRequest.class)))
                .willReturn(PAYMENT_ID);
    }

    private void givenExpirePolicy() {
        ReflectionTestUtils.setField(paymentService, "paymentExpireMinutes", 30);
        ReflectionTestUtils.setField(paymentService, "paymentExpireBatchSize", 500);
    }

    private PaymentConfirmRequest request() {
        return new PaymentConfirmRequest(PAYMENT_KEY, PG_ORDER_ID, ORDER_ID, AMOUNT);
    }

    private TossPaymentClient.TossPayment tossResult(String status) {
        return new TossPaymentClient.TossPayment(
                PAYMENT_KEY, PG_ORDER_ID, status, AMOUNT, AMOUNT, OffsetDateTime.now());
    }

    private Payment approvedPayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId(PG_ORDER_ID).amount(AMOUNT).build();
        payment.approve(PAYMENT_KEY, LocalDateTime.now());
        return payment;
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(PAYMENT_ID, ORDER_ID, PaymentProvider.TOSS, status,
                AMOUNT, 0L, LocalDateTime.now(), null, null);
    }
}
