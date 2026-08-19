package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCancelService 단위 테스트 (V1.1-6)")
class PaymentCancelServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID  = 7L;
    private static final Long AMOUNT   = 20_000L;
    private static final String PAYMENT_KEY = "test_payment_key_123456";
    private static final String REASON = "재고 확보 실패(자동취소)";

    @InjectMocks private PaymentCancelService paymentCancelService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private TossPaymentClient tossPaymentClient;

    @Test
    @DisplayName("전액 환불 — 승인된 결제에 PG 취소를 호출하고 CANCELED 로 전이한다 (§5.1)")
    void cancelForOrder_fullRefund() {
        Payment payment = approvedPayment();
        givenPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(times(1)).cancel(PAYMENT_KEY, REASON, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelledAmount()).isEqualTo(AMOUNT);
        assertThat(payment.getCanceledAt()).isNotNull();
    }

    @Test
    @DisplayName("부분 환불 — 요청 금액만 취소하고 승인 상태를 유지한다 (§4.2)")
    void cancelForOrder_partialRefund() {
        Payment payment = approvedPayment();
        givenPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, 5_000L, "반품 승인");

        then(tossPaymentClient).should(times(1)).cancel(PAYMENT_KEY, "반품 승인", 5_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.cancellableAmount()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("멱등 — 이미 전액 취소된 결제는 PG 재호출 없이 건너뛴다")
    void cancelForOrder_alreadyCancelled_skips() {
        Payment payment = approvedPayment();
        payment.applyCancel(AMOUNT, LocalDateTime.now());
        givenPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(never()).cancel(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("승인된 결제가 없으면 no-op — 보상 경로에서 무조건 호출해도 안전하다")
    void cancelForOrder_noPayment_noOp() {
        given(paymentRepository.findActiveByOrderId(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.empty());

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("승인 전(READY) 결제는 취소하지 않는다")
    void cancelForOrder_readyPayment_skips() {
        givenPayment(readyPayment());

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("0원 결제(§11-3)는 PG 왕복 없이 내부 상태만 취소한다")
    void cancelForOrder_zeroAmountPayment_noPgCall() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.NONE).amount(0L).build();
        payment.approve(null, LocalDateTime.now());
        givenPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, 0L, REASON);

        then(tossPaymentClient).should(never()).cancel(any(), any(), anyLong());
    }

    @Test
    @DisplayName("요청 금액이 잔여 취소가능액을 넘으면 잔여액까지만 취소한다(과환불 방지)")
    void cancelForOrder_capsAtRemainingAmount() {
        Payment payment = approvedPayment();
        payment.applyCancel(15_000L, LocalDateTime.now());
        givenPayment(payment);

        paymentCancelService.cancelForOrder(ORDER_ID, AMOUNT, REASON);

        then(tossPaymentClient).should(times(1)).cancel(PAYMENT_KEY, REASON, 5_000L);
        assertThat(payment.getCancelledAmount()).isEqualTo(AMOUNT);
    }

    private void givenPayment(Payment payment) {
        given(paymentRepository.findActiveByOrderId(ORDER_ID, PaymentStatus.FAILED))
                .willReturn(Optional.of(payment));
    }

    private Payment approvedPayment() {
        Payment payment = readyPayment();
        payment.approve(PAYMENT_KEY, LocalDateTime.now());
        return payment;
    }

    private Payment readyPayment() {
        return Payment.builder()
                .orderId(ORDER_ID).userId(USER_ID).pgProvider(PaymentProvider.TOSS).amount(AMOUNT).build();
    }
}
