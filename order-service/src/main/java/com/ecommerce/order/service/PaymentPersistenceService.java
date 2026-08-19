package com.ecommerce.order.service;

import com.ecommerce.order.client.TossPaymentClient;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PaymentAlreadyApprovedException;
import com.ecommerce.order.exception.PaymentAmountMismatchException;
import com.ecommerce.order.exception.PaymentInProgressException;
import com.ecommerce.order.exception.PaymentNotCompletedException;
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import com.ecommerce.order.support.PgOrderId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 결제 승인 트랜잭션 전담 빈 (payment-foundation §5).
 * PG 호출은 트랜잭션 밖(PaymentService·PaymentRecoveryService)에서 하고, 이 빈은 승인 전후의 DB 상태만
 * 짧은 트랜잭션으로 확정한다.
 *   - 외부 HTTP 왕복 동안 DB 커넥션·락을 잡지 않는다(M-04 와 동일한 이유)
 *   - 승인 실패 보상(FAILED + 주문 취소)이 승인 준비 트랜잭션의 롤백에 휩쓸리지 않는다
 * 별도 빈으로 분리한 이유는 self-invocation 시 프록시를 거치지 않아 @Transactional 이 적용되지 않기 때문이다
 * (OrderPersistenceService 와 동일).
 *
 * 상태 확정 경로는 넷이다:
 *   markApproved  — 승인 확정(응답 대조 통과 시에만)
 *   markFailed    — 승인 실패 확정: 결제 FAILED + 주문 CANCELLED (§5.1)
 *   markUnknown   — 결과 미확정: 결제 UNKNOWN 유지, 주문은 손대지 않는다 (C-01)
 *   abandon       — 고아 결제 정리: 결제만 FAILED, 주문은 결제 대기 유지 → 재결제 허용 (C-02)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {

    /** 재승인을 막는 결제 상태 — 승인됐거나 취소된 결제가 있으면 같은 주문을 다시 승인하지 않는다 (§8) */
    private static final Set<PaymentStatus> APPROVED_STATUSES =
            Set.of(PaymentStatus.APPROVED, PaymentStatus.CANCELED);

    /** 결제 승인 이후 단계에 들어선 주문 상태 — 재승인 요청은 409 로 돌려준다 */
    private static final Set<OrderStatus> PAID_ORDER_STATUSES =
            Set.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    private final PaymentRepository          paymentRepository;
    private final OrderRepository            orderRepository;
    private final NotificationService        notificationService;
    private final PaymentIncidentService     paymentIncidentService;
    private final ApplicationEventPublisher  applicationEventPublisher;

    /**
     * 승인 준비 — 소유자·주문상태·금액·멱등·PG 주문번호를 모두 검증하고 READY 결제를 만든다.
     * 여기를 통과한 요청만 PG 로 나간다.
     * M-03: 주문 행을 잠그고 진행 중 결제까지 확인해 동시 confirm 이 함께 통과하지 못하게 한다.
     * @return 생성된 결제 id
     */
    @Transactional
    public Long prepare(Long userId, PaymentConfirmRequest request) {
        Order order = findOwnedOrderForUpdate(request.orderId(), userId);
        requirePayable(order);
        requireNotApproved(order.getId(), request.paymentKey());
        requireAmountMatches(order, request.amount());

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .userId(userId)
                .pgProvider(PaymentProvider.TOSS)
                // M-05: 시도별 PG 주문번호를 검증해 저장한다 — 재조회(C-01) 키로도 쓴다
                .pgOrderId(PgOrderId.requireForOrder(request.pgOrderId(), order.getId()))
                .amount(order.getPayableAmount())
                .build());
        return payment.getId();
    }

    /**
     * 승인 확정 — 결제 APPROVED + 주문 PAYMENT_PENDING→PENDING 전이.
     * 이 전이 이후에야 order.created 가 발행되어 재고 차감 Saga 가 시작된다(§5: 승인 전 재고 미차감).
     * H-03/M-01: PG 응답이 승인 완료(DONE)이고 우리 결제와 대조되는 경우에만 확정한다 —
     *            대조 실패 시 예외로 롤백하고 호출부가 PG 취소로 보상한다.
     */
    @Transactional
    public PaymentResponse markApproved(Long paymentId, TossPaymentClient.TossPayment result) {
        Payment payment = findPayment(paymentId);
        requireCompletedApproval(payment, result);
        payment.approve(result.paymentKey(), result.approvedAtAsLocal().orElseGet(LocalDateTime::now));

        Order order = findOrder(payment.getOrderId());
        order.markPaid();
        applicationEventPublisher.publishEvent(OrderCreatedApplicationEvent.from(order));

        log.info("결제 승인 완료. orderId={}, amount={}", order.getId(), payment.getAmount());
        return PaymentResponse.from(payment);
    }

    /**
     * 승인 실패 보상 — 결제 FAILED + 주문 CANCELLED (§5.1).
     * 승인이 안 된 주문이므로 재고 복구는 필요 없다(차감 전 단계).
     */
    @Transactional
    public void markFailed(Long paymentId, String reason) {
        Payment payment = findPayment(paymentId);
        payment.fail(reason);

        Order order = findOrder(payment.getOrderId());
        order.cancel();
        notificationService.create(order.getUserId(), NotificationType.ORDER_CANCELLED, order.getId());
        log.warn("결제 승인 실패로 주문 취소. orderId={}", order.getId());
    }

    /**
     * C-01: 결과 미확정 확정 — 재조회로도 승인 여부를 판정하지 못한 결제를 UNKNOWN 으로 남긴다.
     * FAILED 로 덮으면 조회·환불 경로에서 사라지므로(돈 유실) 상태를 남기고, 주문도 취소하지 않는다
     * (진행 중 결제가 있는 주문은 만료 대상에서 제외되어 운영자가 정산할 시간을 확보한다).
     * 미결 건은 실패주문 대장에 별도 트랜잭션으로 기록해 관리자가 반드시 찾을 수 있게 한다.
     */
    @Transactional
    public void markUnknown(Long paymentId, String pgPaymentKey, String reason) {
        Payment payment = findPayment(paymentId);
        payment.markUnknown(pgPaymentKey, reason);
        paymentIncidentService.record(payment.getOrderId(), payment.getUserId(), reason);
        log.error("결제 결과 미확정 — 운영 확인 필요. orderId={}, paymentId={}, 사유={}",
                payment.getOrderId(), paymentId, reason);
    }

    /**
     * C-02: 고아 결제 정리 — 승인되지 않은 것이 확인된 진행 중 결제를 FAILED 로 닫는다.
     * 주문은 결제 대기로 그대로 두어(취소하지 않아) 사용자가 재결제할 수 있게 한다 —
     * READY 행이 부분 유니크 인덱스를 점유해 재결제가 409 로 영구 차단되는 문제를 푸는 지점이다.
     */
    @Transactional
    public void abandon(Long paymentId, String reason) {
        Payment payment = findPayment(paymentId);
        payment.fail(reason);
        log.warn("진행 중 결제 정리 — 재결제 허용. orderId={}, paymentId={}, 사유={}",
                payment.getOrderId(), paymentId, reason);
    }

    /**
     * H-03/M-01: 승인 응답 대조 — status=DONE, 거래키 존재, PG 주문번호·금액 일치를 모두 확인한다.
     * 가상계좌 입금대기(WAITING_FOR_DEPOSIT)·승인 진행 중(IN_PROGRESS) 응답으로 확정하면
     * 미입금 주문이 재고 차감·배송까지 진행되므로 이번 범위에서는 지원하지 않고 거부한다.
     */
    private void requireCompletedApproval(Payment payment, TossPaymentClient.TossPayment result) {
        if (!result.isDone() || result.paymentKey() == null || result.paymentKey().isBlank()) {
            throw new PaymentNotCompletedException(
                    "결제가 완료되지 않았습니다. 지원하지 않는 결제 수단이거나 승인이 끝나지 않았습니다.");
        }
        if (!Objects.equals(result.totalAmount(), payment.getAmount())
                || !Objects.equals(result.orderId(), payment.getPgOrderId())) {
            log.error("PG 승인 응답 대조 실패 — 확정하지 않는다. orderId={}, paymentId={}",
                    payment.getOrderId(), payment.getId());
            throw new PaymentNotCompletedException("결제 승인 결과가 주문 정보와 일치하지 않습니다.");
        }
    }

    /** 결제 가능한 주문인지 — 이미 승인 단계를 지났으면 409, 취소·만료 주문이면 409(상태 충돌) */
    private void requirePayable(Order order) {
        if (order.isAwaitingPayment()) {
            return;
        }
        if (PAID_ORDER_STATUSES.contains(order.getStatus())) {
            throw new PaymentAlreadyApprovedException(order.getId());
        }
        throw new IllegalStateException("결제할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus());
    }

    /**
     * 승인 멱등 — 주문 기준·PG 거래키 기준 모두 재승인을 막는다 (§8).
     * M-03: 진행 중(READY)·미확정(UNKNOWN) 결제가 있으면 이중 출금 위험이 있어 새 승인을 받지 않는다.
     */
    private void requireNotApproved(Long orderId, String paymentKey) {
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, APPROVED_STATUSES)) {
            throw new PaymentAlreadyApprovedException(orderId);
        }
        if (!paymentRepository.findByOrderIdAndStatusIn(
                orderId, PaymentStatus.inFlightStatuses()).isEmpty()) {
            throw new PaymentInProgressException(orderId);
        }
        paymentRepository.findByPgPaymentKey(paymentKey).ifPresent(existing -> {
            throw new PaymentAlreadyApprovedException(existing.getOrderId());
        });
    }

    /** 금액 위변조 차단 — 클라이언트가 보낸 금액이 서버 계산 결제금액과 다르면 승인하지 않는다 (§8) */
    private void requireAmountMatches(Order order, Long requestedAmount) {
        if (!Objects.equals(order.getPayableAmount(), requestedAmount)) {
            log.warn("결제 금액 불일치 — 승인 거부. orderId={}", order.getId());
            throw new PaymentAmountMismatchException(order.getId());
        }
    }

    /** 본인 소유 주문 조회(행 잠금) — 타인 주문은 404 (정보 노출 방지) */
    private Order findOwnedOrderForUpdate(Long orderId, Long userId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
