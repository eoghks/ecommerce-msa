package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 결과 미확정 복구 (C-01, C-02).
 * "돈은 빠졌을 수 있는데 우리 DB 는 모르는" 구간을 정리하는 전담 빈이다.
 *   - 재조회: GET /v1/payments/orders/{pgOrderId} 로 실제 승인 여부를 확인한다
 *   - 승인 확인 → 정상 확정 / 미승인 확인 → 실패 확정 / 판정 불가 → UNKNOWN 으로 남겨 운영자가 찾게 한다
 *   - 승인 후 우리 확정이 실패하면 즉시 PG 취소(자동 환불)로 보상한다 — 승인된 돈이 추적 불가가 되지 않게
 *   - 고아 진행 중 결제를 정리해 재결제가 유니크 제약에 영구 차단되지 않게 한다
 * PG HTTP 호출이 있으므로 트랜잭션을 걸지 않고, DB 확정만 PaymentPersistenceService(짧은 트랜잭션)에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    /** 승인 후 확정 실패 보상 취소 사유 — PG 에 그대로 전달된다 */
    private static final String COMPENSATE_REASON = "주문 확정 실패로 자동 취소";

    /** 통신 실패 후 재조회에서 미승인이 확인된 경우의 결제 실패 사유 */
    private static final String NOT_APPROVED_REASON = "PG 승인 미완료(통신 실패 후 재조회로 확인)";

    /** 재조회로도 판정하지 못한 경우의 미결 사유 */
    private static final String UNRESOLVED_REASON = "결제 승인 결과 미확정 — PG 재조회 판정 불가";

    /** 승인은 됐는데 확정·보상 취소까지 실패한 경우의 미결 사유(최우선 수동 처리 대상) */
    private static final String COMPENSATE_FAILED_REASON = "승인 후 확정 실패 + PG 취소 실패 — 수동 환불 필요";

    /** 보상 취소가 성공해 결제를 실패로 닫을 때의 사유 */
    private static final String COMPENSATED_REASON = "주문 확정 실패로 PG 취소 완료";

    /** 고아 진행 중 결제를 닫을 때의 사유 — 주문은 결제 대기로 남아 재결제할 수 있다 */
    private static final String STALE_ABANDON_REASON = "결제 미완료(진행 중 결제 정리)";

    /** 사용자에게 보여줄 재확인 안내 — 실패로 단정하지 않는다 */
    private static final String RETRY_MESSAGE =
            "결제 결과를 확인하지 못했습니다. 주문 내역에서 결제 상태를 확인해주세요.";

    /** 보상 취소 멱등키 — 같은 결제의 보상 재시도가 이중 환불되지 않게 결제 id 로 고정한다 (H-01) */
    private static final String COMPENSATE_KEY_FORMAT = "PAYCMP-%d";

    private final PaymentPersistenceService paymentPersistenceService;
    private final TossPaymentClient         tossPaymentClient;
    private final PaymentRepository         paymentRepository;

    /** 진행 중 결제를 회수 대상으로 보기까지의 대기 분 — PG 승인 왕복 상한보다 충분히 크게 잡는다 */
    @Value("${app.order.payment-inflight-stale-minutes:3}")
    private int inFlightStaleMinutes;

    /** 회수 1회 실행당 처리 상한 — 적체 시에도 스케줄 배치가 길어지지 않게 제한한다 */
    @Value("${app.order.payment-reconcile-batch-size:100}")
    private int reconcileBatchSize;

    /**
     * C-01: 승인 결과 미확정 복구 — 통신 실패로 승인 응답을 못 받은 결제를 재조회로 판정한다.
     * 승인 확인 → 정상 확정(200), 미승인 확인 → 실패 확정 후 502, 판정 불가 → UNKNOWN 후 502.
     */
    public PaymentResponse resolveUnconfirmed(Long paymentId) {
        Payment payment = findPayment(paymentId);
        Optional<TossPaymentClient.TossPayment> found = lookup(payment);
        if (found.filter(TossPaymentClient.TossPayment::isDone).isPresent()) {
            log.warn("통신 실패 후 재조회에서 승인 확인 — 정상 확정으로 진행. orderId={}", payment.getOrderId());
            return confirmApproved(paymentId, payment.getAmount(), found.get());
        }
        if (found.isEmpty() || found.get().isTerminated()) {
            // 접수 자체가 없거나 종료된 결제 — 출금이 없으므로 실패로 확정한다(주문도 취소 보상)
            paymentPersistenceService.markFailed(paymentId, NOT_APPROVED_REASON);
            throw new PaymentGatewayUnavailableException(RETRY_MESSAGE);
        }
        // 승인 진행 중·입금대기 등 — 결과가 아직 열려 있어 실패로 단정할 수 없다
        paymentPersistenceService.markUnknown(paymentId, found.get().paymentKey(), UNRESOLVED_REASON);
        throw new PaymentGatewayUnavailableException(RETRY_MESSAGE);
    }

    /**
     * C-02: 승인 확정 + 확정 실패 시 보상.
     * 만료 스케줄러와의 경쟁(order.markPaid 실패)·DB 오류 등으로 확정 트랜잭션이 실패하면
     * 승인된 금액을 즉시 PG 취소해 "승인된 돈이 추적 불가" 상태가 되지 않게 한다.
     */
    public PaymentResponse confirmApproved(Long paymentId, long approvedAmount,
                                           TossPaymentClient.TossPayment approved) {
        try {
            return paymentPersistenceService.markApproved(paymentId, approved);
        } catch (RuntimeException ex) {
            // 흐름 제어가 아니라 "승인 성공 후 확정 실패"에 대한 보상 트랜잭션 진입점이다
            log.error("승인 성공 후 확정 실패 — PG 취소 보상 시작. paymentId={}, 원인={}",
                    paymentId, ex.getClass().getSimpleName());
            compensateApproval(paymentId, approvedAmount, approved);
            throw ex;
        }
    }

    /**
     * C-02: 승인분 PG 취소 보상 — 취소 성공 시 결제 실패 확정(주문 취소), 실패 시 UNKNOWN + 미결 기록.
     * 어떤 경우에도 승인 사실이 우리 쪽에서 사라지지 않게 한다.
     */
    public void compensateApproval(Long paymentId, long approvedAmount,
                                   TossPaymentClient.TossPayment approved) {
        try {
            tossPaymentClient.cancel(approved.paymentKey(), COMPENSATE_REASON,
                    cancelAmountOf(approved, approvedAmount), compensateKey(paymentId));
            paymentPersistenceService.markFailed(paymentId, COMPENSATED_REASON);
            log.warn("확정 실패 보상 완료 — PG 취소 후 결제 실패 처리. paymentId={}", paymentId);
        } catch (RuntimeException ex) {
            // 보상까지 실패 — 상태를 UNKNOWN 으로 남기고 대장·알림으로 운영자에게 넘긴다
            paymentPersistenceService.markUnknown(paymentId, approved.paymentKey(),
                    COMPENSATE_FAILED_REASON);
        }
    }

    /**
     * C-02: 재결제 차단 해소 — 주문에 오래 남은 진행 중 결제를 재조회로 정리한다.
     * 고아 READY 는 부분 유니크 인덱스(uq_payment_order_active)를 점유해 재결제를 영구히 막으므로,
     * 미승인이 확인되면 결제만 FAILED 로 닫아(주문은 결제 대기 유지) 재시도를 허용한다.
     * 방금 시작된 결제는 대상이 아니다 — 동시 승인 차단은 prepare 가 담당한다(M-03).
     */
    public void releaseStalePayments(Long orderId, Long userId) {
        List<Payment> stale = paymentRepository.findStaleByOrderId(
                orderId, userId, PaymentStatus.inFlightStatuses(), staleThreshold());
        stale.forEach(this::resolveIsolated);
    }

    /**
     * C-01/C-02: 진행 중·미확정 결제 정기 회수 (스케줄러).
     * 프로세스 종료·재시작으로 확정 경로를 놓친 결제를 재조회로 확정해 고아 결제가 남지 않게 한다.
     * @return 상태를 확정한 건수
     */
    public int reconcileInFlightPayments() {
        List<Payment> targets = paymentRepository.findStalePayments(
                PaymentStatus.inFlightStatuses(), staleThreshold(),
                PageRequest.of(0, reconcileBatchSize));

        int resolved = 0;
        for (Payment payment : targets) {
            resolved += resolveIsolated(payment) ? 1 : 0;
        }
        if (!targets.isEmpty()) {
            log.warn("진행 중 결제 회수 실행. 대상={}, 확정={}, 기준분={}",
                    targets.size(), resolved, inFlightStaleMinutes);
        }
        return resolved;
    }

    /**
     * 결제 1건 회수를 격리 실행한다 — 한 건의 실패가 배치·재결제 요청 전체를 막지 않게 한다.
     * @return 상태가 확정됐으면 true
     */
    private boolean resolveIsolated(Payment payment) {
        try {
            return resolveStale(payment);
        } catch (RuntimeException ex) {
            log.error("진행 중 결제 회수 실패 — 해당 건만 건너뛴다. paymentId={}, 원인={}",
                    payment.getId(), ex.getClass().getSimpleName());
            return false;
        }
    }

    /** 진행 중 결제 1건 판정 — 승인됐으면 확정, 미승인이면 결제만 닫고, 판정 불가면 UNKNOWN 으로 남긴다 */
    private boolean resolveStale(Payment payment) {
        Optional<TossPaymentClient.TossPayment> found = lookup(payment);
        if (found.filter(TossPaymentClient.TossPayment::isDone).isPresent()) {
            confirmApproved(payment.getId(), payment.getAmount(), found.get());
            return true;
        }
        if (found.isEmpty() || found.get().isTerminated()) {
            paymentPersistenceService.abandon(payment.getId(), STALE_ABANDON_REASON);
            return true;
        }
        paymentPersistenceService.markUnknown(payment.getId(), found.get().paymentKey(), UNRESOLVED_REASON);
        return false;
    }

    /**
     * PG 재조회 — 판정 불가(통신 실패·PG 주문번호 없음)면 UNKNOWN 으로 남긴 뒤 예외를 올린다.
     * 0원 결제처럼 PG 주문번호가 없는 결제는 재조회 대상이 아니다.
     */
    private Optional<TossPaymentClient.TossPayment> lookup(Payment payment) {
        String pgOrderId = payment.getPgOrderId();
        if (pgOrderId == null || pgOrderId.isBlank()) {
            paymentPersistenceService.markUnknown(payment.getId(), payment.getPgPaymentKey(),
                    UNRESOLVED_REASON);
            throw new PaymentGatewayUnavailableException(RETRY_MESSAGE);
        }
        try {
            return tossPaymentClient.findByPgOrderId(pgOrderId);
        } catch (PaymentGatewayUnavailableException ex) {
            paymentPersistenceService.markUnknown(payment.getId(), payment.getPgPaymentKey(),
                    UNRESOLVED_REASON);
            throw ex;
        }
    }

    /** 보상 취소 금액 — PG 응답 금액을 우선하고, 없으면 우리 승인 금액을 쓴다 */
    private long cancelAmountOf(TossPaymentClient.TossPayment approved, long approvedAmount) {
        return Optional.ofNullable(approved.totalAmount()).orElse(approvedAmount);
    }

    private String compensateKey(Long paymentId) {
        return String.format(COMPENSATE_KEY_FORMAT, paymentId);
    }

    private LocalDateTime staleThreshold() {
        return LocalDateTime.now().minusMinutes(inFlightStaleMinutes);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
