package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import com.ecommerce.order.support.PgOrderId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 결제 서비스 (V1.1-6, payment-foundation §5).
 * - 승인: 주문 검증(소유자·상태·금액·멱등) → PG 승인 → 주문 PENDING 전이 → 재고 차감 Saga 시작
 * - 실패: 결제 FAILED + 주문 CANCELLED 로 보상하고 사유를 그대로 전달한다(§5.1)
 * - 만료: 결제 대기로 방치된 주문을 정책 시간 후 일괄 취소한다(§11.1)
 * PG 호출은 트랜잭션 밖에서 수행하고 DB 확정만 PaymentPersistenceService 에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 결제 만료로 취소된 주문에 남기는 사유 */
    private static final String EXPIRE_REASON = "결제 미완료(자동취소)";

    private final PaymentPersistenceService paymentPersistenceService;
    private final TossPaymentClient         tossPaymentClient;
    private final PaymentRepository         paymentRepository;
    private final OrderRepository           orderRepository;

    /** 결제 대기 주문을 만료 처리하기까지의 대기 시간(정책값, §11.1) */
    @Value("${app.order.payment-expire-minutes:30}")
    private int paymentExpireMinutes;

    /**
     * 결제 승인 — 결제위젯 성공 후 프론트가 전달한 (paymentKey, orderId, amount)로 최종 승인한다.
     * 금액은 서버가 계산한 payableAmount 와 일치할 때만 승인한다(위변조 차단, §8).
     */
    public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
        requireUser(userId);
        Long paymentId = paymentPersistenceService.prepare(userId, request);
        try {
            TossPaymentClient.TossPayment approved = tossPaymentClient.confirm(
                    request.paymentKey(), PgOrderId.of(request.orderId()), request.amount());
            return paymentPersistenceService.markApproved(paymentId, approved);
        } catch (PaymentApprovalFailedException ex) {
            // 보상 처리 — 예외를 흐름 제어로 쓰는 것이 아니라 PG 실패에 대한 보상 트랜잭션이다(§5.1)
            paymentPersistenceService.markFailed(paymentId, ex.getMessage());
            throw ex;
        }
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
     * 조건부 벌크 UPDATE 라 다중 인스턴스가 동시에 실행해도 같은 주문을 두 번 취소하지 않는다.
     * 승인 전 단계라 재고 복구·환불 대상이 없다.
     * @return 만료 처리된 주문 수
     */
    @Transactional
    public int expireUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(paymentExpireMinutes);
        int expired = orderRepository.expirePaymentPendingOrders(
                OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED, threshold, now);
        if (expired > 0) {
            log.info("결제 미완료 주문 만료. 건수={}, 기준분={}, 사유={}",
                    expired, paymentExpireMinutes, EXPIRE_REASON);
        }
        return expired;
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
