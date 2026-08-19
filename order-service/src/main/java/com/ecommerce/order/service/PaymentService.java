package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.FailedOrderLog;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 결제 서비스 (V1.1-6, payment-foundation §5).
 * - 승인: 주문 검증(소유자·상태·금액·멱등) → PG 승인 → 주문 PENDING 전이 → 재고 차감 Saga 시작
 * - 실패: PG 가 명시적으로 거절한 경우에만 결제 FAILED + 주문 CANCELLED 로 보상한다(§5.1)
 * - 미확정(C-01): 통신 실패는 확정 실패로 단정하지 않고 재조회로 판정한다(PaymentRecoveryService)
 * - 만료: 결제 대기로 방치된 주문을 정책 시간 후 취소한다(§11.1). 진행 중 결제가 있는 주문은 제외(C-02)
 * PG 호출은 트랜잭션 밖에서 수행하고 DB 확정만 PaymentPersistenceService 에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 결제 만료로 취소된 주문에 남기는 사유 — M-07: 로그뿐 아니라 실패주문 대장에도 기록한다 */
    private static final String EXPIRE_REASON = "결제 미완료(자동취소)";

    /** M-01: 요청 거래키와 다른 승인 응답을 받은 경우의 보상 사유 */
    private static final String KEY_MISMATCH_REASON = "요청과 다른 PG 거래키 응답";

    private final PaymentPersistenceService paymentPersistenceService;
    private final PaymentRecoveryService    paymentRecoveryService;
    private final TossPaymentClient         tossPaymentClient;
    private final PaymentRepository         paymentRepository;
    private final OrderRepository           orderRepository;
    private final FailedOrderLogRepository  failedOrderLogRepository;

    /** 결제 대기 주문을 만료 처리하기까지의 대기 시간(정책값, §11.1) */
    @Value("${app.order.payment-expire-minutes:30}")
    private int paymentExpireMinutes;

    /** M-07: 만료 1회 실행당 처리 상한 — 적체 시 10분 주기 롱 트랜잭션이 되지 않게 제한한다 */
    @Value("${app.order.payment-expire-batch-size:500}")
    private int paymentExpireBatchSize;

    /**
     * 결제 승인 — 결제위젯 성공 후 프론트가 전달한 (paymentKey, pgOrderId, orderId, amount)로 최종 승인한다.
     * 금액은 서버가 계산한 payableAmount 와 일치할 때만 승인한다(위변조 차단, §8).
     * C-02: 고아 진행 중 결제를 먼저 회수해야 유니크 제약이 재결제를 영구 차단하지 않는다.
     */
    public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
        requireUser(userId);
        paymentRecoveryService.releaseStalePayments(request.orderId(), userId);
        Long paymentId = paymentPersistenceService.prepare(userId, request);
        return approve(paymentId, request);
    }

    /**
     * PG 승인 호출과 결과 처리 — 실패를 성격별로 나눈다 (C-01).
     *   PG 명시적 거절: 결제 실패 + 주문 취소 확정(§5.1)
     *   통신 실패(응답 미수신): 확정하지 않고 재조회로 판정 — 이미 출금됐을 수 있다
     *   승인 성공: 응답 대조 후 확정하며, 확정 실패 시 PG 취소로 보상한다(C-02)
     */
    private PaymentResponse approve(Long paymentId, PaymentConfirmRequest request) {
        TossPaymentClient.TossPayment approved;
        try {
            approved = tossPaymentClient.confirm(
                    request.paymentKey(), request.pgOrderId(), request.amount());
        } catch (PaymentApprovalFailedException ex) {
            // 보상 처리 — 예외를 흐름 제어로 쓰는 것이 아니라 PG 거절에 대한 보상 트랜잭션이다(§5.1)
            paymentPersistenceService.markFailed(paymentId, ex.getMessage());
            throw ex;
        } catch (PaymentGatewayUnavailableException ex) {
            // C-01: 타임아웃·연결 실패 — 승인됐을 수 있으므로 재조회로 실제 결과를 확인한다
            log.warn("PG 통신 실패 — 승인 결과 재조회로 판정한다. orderId={}", request.orderId());
            return paymentRecoveryService.resolveUnconfirmed(paymentId);
        }
        requireSamePaymentKey(paymentId, request, approved);
        return paymentRecoveryService.confirmApproved(paymentId, request.amount(), approved);
    }

    /**
     * M-01: 요청 거래키와 응답 거래키 대조 — 불일치 응답으로는 확정하지 않고 승인분을 보상 취소한다.
     * 금액·PG 주문번호·승인 상태 대조는 확정 트랜잭션(PaymentPersistenceService)이 담당한다(H-03).
     */
    private void requireSamePaymentKey(Long paymentId, PaymentConfirmRequest request,
                                       TossPaymentClient.TossPayment approved) {
        if (Objects.equals(request.paymentKey(), approved.paymentKey())) {
            return;
        }
        log.error("승인 응답 거래키 불일치 — 확정하지 않고 보상 취소한다. orderId={}", request.orderId());
        paymentRecoveryService.compensateApproval(paymentId, request.amount(), approved);
        throw new PaymentNotCompletedException("결제 승인 결과가 요청과 일치하지 않습니다. " + KEY_MISMATCH_REASON);
    }

    /** 주문 결제 상태 조회 — 본인 결제만. 없거나 타인 것이면 404 (정보 노출 방지) */
    @Transactional(readOnly = true)
    public PaymentResponse getByOrder(Long orderId, Long userId) {
        requireUser(userId);
        Payment payment = paymentRepository.findActiveByOrderId(orderId, PaymentStatus.FAILED)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
        return PaymentResponse.from(payment);
    }

    /**
     * 미완료 주문 만료 (§11.1) — 결제 대기로 방치된 주문을 CANCELLED 로 전이한다.
     * 건별 조건부 UPDATE 라 다중 인스턴스가 동시에 실행해도 같은 주문을 두 번 취소하지 않는다.
     * C-02: 진행 중(READY)·미확정(UNKNOWN) 결제가 있는 주문은 제외한다 — PG 왕복 중 만료시키면
     *       승인된 결제가 주문 취소로 덮여 환불 경로가 사라진다.
     * M-07: 1회 실행 상한을 두고, 만료 사유를 실패주문 대장에 남겨 CS 추적이 가능하게 한다.
     * @return 만료 처리된 주문 수
     */
    @Transactional
    public int expireUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(paymentExpireMinutes);
        List<PaymentExpireTarget> targets = orderRepository.findExpirableOrders(
                OrderStatus.PAYMENT_PENDING, PaymentStatus.inFlightStatuses(), threshold,
                PageRequest.of(0, paymentExpireBatchSize));

        int expired = 0;
        for (PaymentExpireTarget target : targets) {
            expired += expireOne(target, threshold, now);
        }
        if (expired > 0) {
            log.info("결제 미완료 주문 만료. 건수={}, 대상={}, 기준분={}, 사유={}",
                    expired, targets.size(), paymentExpireMinutes, EXPIRE_REASON);
        }
        return expired;
    }

    /** 만료 1건 — 조건부 UPDATE 로 원자 처리하고, 실제 전이된 건만 사유를 대장에 남긴다 (M-07) */
    private int expireOne(PaymentExpireTarget target, LocalDateTime threshold, LocalDateTime now) {
        int updated = orderRepository.expireIfStillUnpaid(target.orderId(),
                OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED,
                PaymentStatus.inFlightStatuses(), threshold, now);
        if (updated == 0) {
            return 0;   // 다른 인스턴스가 선점했거나 그 사이 결제가 시작된 주문
        }
        failedOrderLogRepository.save(FailedOrderLog.builder()
                .orderId(target.orderId())
                .userId(target.userId())
                .reason(EXPIRE_REASON)
                .build());
        return 1;
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
