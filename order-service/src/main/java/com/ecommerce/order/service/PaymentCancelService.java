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
 * 재고 차감 실패 보상, 사용자 주문 취소, 반품 승인 환불이 모두 이 진입점을 쓴다.
 * 승인된 결제가 없으면 아무 것도 하지 않으므로 보상 경로에서 무조건 호출해도 안전하다.
 * PG 취소가 실패하면 예외가 호출 트랜잭션을 롤백시켜 "환불 안 됐는데 환불 완료" 상태를 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    /**
     * 주문 결제 취소 — requestedAmount 만큼 환불한다(잔여 취소가능액을 넘지 않는다).
     * 이미 전액 취소된 결제·미승인 결제는 건너뛴다(멱등, 이중 환불 방지).
     */
    @Transactional
    public void cancelForOrder(Long orderId, long requestedAmount, String reason) {
        Optional<Payment> found = paymentRepository.findActiveByOrderId(orderId, PaymentStatus.FAILED);
        if (found.isEmpty() || !found.get().isApproved()) {
            log.info("취소할 승인 결제 없음 — 환불 생략. orderId={}", orderId);
            return;
        }

        Payment payment = found.get();
        long cancelAmount = Math.min(requestedAmount, payment.cancellableAmount());
        if (cancelAmount <= 0) {
            log.info("취소 가능 잔액 없음 — 환불 생략(멱등). orderId={}", orderId);
            return;
        }

        if (payment.requiresPgCall()) {
            tossPaymentClient.cancel(payment.getPgPaymentKey(), reason, cancelAmount);
        }
        payment.applyCancel(cancelAmount, LocalDateTime.now());
        log.info("결제 취소 완료. orderId={}, 취소액={}, 누적취소액={}",
                orderId, cancelAmount, payment.getCancelledAmount());
    }
}
