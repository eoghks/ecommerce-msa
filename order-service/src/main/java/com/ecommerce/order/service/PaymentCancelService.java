package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 결제 취소·환불 (payment-foundation §4.2, §5.1).
 * 재고 차감 실패 보상, 사용자 주문 취소, 항목취소, 반품 승인 환불이 모두 이 진입점을 쓴다.
 * 승인된 결제가 없으면 아무 것도 하지 않으므로 보상 경로에서 무조건 호출해도 안전하다.
 * PG 취소가 실패하면 예외가 호출 트랜잭션을 롤백시켜 "환불 안 됐는데 환불 완료" 상태를 막는다.
 *
 * 정합성 장치:
 *   H-01 멱등키 — PG 취소 성공 후 우리 커밋이 실패해도 재시도가 같은 키로 나가 이중 환불이 걸러진다
 *   H-02 비관적 락 — 동시 부분환불이 같은 누적 취소액을 읽고 덮어써 초과환불되는 것을 막는다
 *   H-04 미결 기록 — 환불 실패는 별도 트랜잭션(REQUIRES_NEW)으로 대장에 남겨 롤백에 휩쓸리지 않게 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    /**
     * H-01: 취소 멱등키 규칙 — 결제 id + 취소 직전 누적 취소액(= 취소 회차).
     * 같은 회차의 재시도는 동일 키가 되어 PG 가 중복 환불을 걸러주고, 다음 회차는 키가 달라 정상 처리된다.
     */
    private static final String IDEMPOTENCY_KEY_FORMAT = "PAYCNL-%d-%d";

    /** H-04: 환불 실패 미결 기록 사유 — 거래키·카드정보는 담지 않는다 */
    private static final String REFUND_FAILED_REASON = "PG 환불 실패 — 재시도·수동 환불 필요";

    private final PaymentRepository      paymentRepository;
    private final PaymentIncidentService paymentIncidentService;
    private final TossPaymentClient      tossPaymentClient;

    /**
     * 주문 결제 취소 — requestedAmount 만큼 환불한다(잔여 취소가능액을 넘지 않는다).
     * 이미 전액 취소된 결제·미승인 결제는 건너뛴다(멱등, 이중 환불 방지).
     */
    @Transactional
    public void cancelForOrder(Long orderId, long requestedAmount, String reason) {
        // H-02: 잔여 취소가능액 계산이 동시 요청과 겹치지 않게 결제 행을 잠그고 읽는다
        Optional<Payment> found =
                paymentRepository.findActiveByOrderIdForUpdate(orderId, PaymentStatus.FAILED);
        if (found.isEmpty() || !found.get().isApproved()) {
            log.info("취소할 승인 결제 없음 — 환불 생략. orderId={}", orderId);
            return;
        }

        Payment payment = found.get();
        long cancelAmount = Math.min(requestedAmount, payment.cancellableAmount());
        if (cancelAmount <= 0) {
            settleZeroAmountCancel(payment, orderId);
            return;
        }

        if (payment.requiresPgCall()) {
            requestPgCancel(payment, reason, cancelAmount);
        }
        payment.applyCancel(cancelAmount, LocalDateTime.now());
        log.info("결제 취소 완료. orderId={}, 취소액={}, 누적취소액={}",
                orderId, cancelAmount, payment.getCancelledAmount());
    }

    /**
     * M-06: 취소 금액이 0인 경우 처리.
     * 0원 결제(§11-3)는 금액 조건으로는 항상 걸러져 주문은 CANCELLED 인데 결제만 APPROVED 로 남는다 →
     * 금액과 무관하게 상태를 CANCELED 로 전이시킨다. 그 밖의 경우는 이미 전액 취소된 멱등 재요청이다.
     */
    private void settleZeroAmountCancel(Payment payment, Long orderId) {
        if (!payment.isZeroAmountApproved()) {
            log.info("취소 가능 잔액 없음 — 환불 생략(멱등). orderId={}", orderId);
            return;
        }
        payment.applyCancel(0L, LocalDateTime.now());
        log.info("0원 결제 취소 — PG 왕복 없이 상태만 전이. orderId={}", orderId);
    }

    /**
     * PG 취소 호출 — 멱등키를 함께 보낸다 (H-01).
     * H-04: 실패는 별도 트랜잭션으로 대장에 남긴 뒤 예외를 그대로 올린다 —
     *       호출부 롤백으로 기록·알림까지 사라져 미환불 주문이 조용히 방치되는 것을 막는다.
     */
    private void requestPgCancel(Payment payment, String reason, long cancelAmount) {
        try {
            tossPaymentClient.cancel(payment.getPgPaymentKey(), reason, cancelAmount,
                    idempotencyKey(payment));
        } catch (RuntimeException ex) {
            paymentIncidentService.record(payment.getOrderId(), payment.getUserId(),
                    REFUND_FAILED_REASON + " 요청취소액=" + cancelAmount);
            throw ex;
        }
    }

    /** H-01: 취소 회차 기준 멱등키 — 같은 회차 재시도는 PG 가 같은 요청으로 인식한다 */
    private String idempotencyKey(Payment payment) {
        return String.format(IDEMPOTENCY_KEY_FORMAT, payment.getId(), payment.getCancelledAmount());
    }
}
