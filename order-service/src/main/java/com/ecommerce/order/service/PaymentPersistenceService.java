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
import com.ecommerce.order.exception.PaymentNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
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
 * PG 호출은 트랜잭션 밖(PaymentService)에서 하고, 이 빈은 승인 전후의 DB 상태만 짧은 트랜잭션으로 확정한다.
 *   - 외부 HTTP 왕복 동안 DB 커넥션·락을 잡지 않는다(M-04 와 동일한 이유)
 *   - 승인 실패 보상(FAILED + 주문 취소)이 승인 준비 트랜잭션의 롤백에 휩쓸리지 않는다
 * 별도 빈으로 분리한 이유는 self-invocation 시 프록시를 거치지 않아 @Transactional 이 적용되지 않기 때문이다
 * (OrderPersistenceService 와 동일).
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
    private final ApplicationEventPublisher  applicationEventPublisher;

    /**
     * 승인 준비 — 소유자·주문상태·금액·멱등을 모두 검증하고 READY 결제를 만든다.
     * 여기를 통과한 요청만 PG 로 나간다.
     * @return 생성된 결제 id
     */
    @Transactional
    public Long prepare(Long userId, PaymentConfirmRequest request) {
        Order order = findOwnedOrder(request.orderId(), userId);
        requirePayable(order);
        requireNotApproved(order.getId(), request.paymentKey());
        requireAmountMatches(order, request.amount());

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .userId(userId)
                .pgProvider(PaymentProvider.TOSS)
                .amount(order.getPayableAmount())
                .build());
        return payment.getId();
    }

    /**
     * 승인 확정 — 결제 APPROVED + 주문 PAYMENT_PENDING→PENDING 전이.
     * 이 전이 이후에야 order.created 가 발행되어 재고 차감 Saga 가 시작된다(§5: 승인 전 재고 미차감).
     */
    @Transactional
    public PaymentResponse markApproved(Long paymentId, TossPaymentClient.TossPayment result) {
        Payment payment = findPayment(paymentId);
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

    /** 승인 멱등 — 주문 기준·PG 거래키 기준 모두 재승인을 막는다 (§8) */
    private void requireNotApproved(Long orderId, String paymentKey) {
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, APPROVED_STATUSES)) {
            throw new PaymentAlreadyApprovedException(orderId);
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

    /** 본인 소유 주문 조회 — 타인 주문은 404 (정보 노출 방지) */
    private Order findOwnedOrder(Long orderId, Long userId) {
        Order order = findOrder(orderId);
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
