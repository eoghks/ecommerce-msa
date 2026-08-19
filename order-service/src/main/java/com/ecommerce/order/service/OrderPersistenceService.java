package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 저장 + ApplicationEvent 등록 전담 빈.
 *
 * OrderService에서 분리한 이유 (StockDecreaseTransactionService와 동일):
 *   기존엔 OrderService.createOrder()가 같은 클래스의 @Transactional 메서드를
 *   self-invocation 으로 호출 → Spring 프록시 미경유 → @Transactional 미적용 →
 *   트랜잭션이 없어 @TransactionalEventListener(AFTER_COMMIT)가 이벤트를 버려
 *   order.created 가 발행되지 않던 버그.
 *   별도 빈으로 분리하면 프록시를 통해 호출되어 트랜잭션이 정상 적용된다.
 *
 * V1.1-6: 주문은 PAYMENT_PENDING 으로 저장되며 order.created(재고 차감 Saga)는 결제 승인 이후에 발행된다.
 *         단, payableAmount == 0 이면 PG 왕복 없이 즉시 결제완료 처리한다(§11-3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public OrderResponse saveAndPublish(Long userId, long totalPrice,
                                        List<OrderItem> items, ShippingInfo shipping) {
        Order order = Order.builder()
                .userId(userId)
                .totalPrice(totalPrice)
                .receiver(shipping.receiver())
                .phone(shipping.phone())
                .address(shipping.address())
                .items(items)
                .build();
        Order savedOrder = orderRepository.save(order);

        settleZeroAmountOrder(savedOrder);

        log.info("주문 생성 완료. orderId={}, userId={}, payableAmount={}, status={}",
                savedOrder.getId(), userId, savedOrder.getPayableAmount(), savedOrder.getStatus());
        return OrderResponse.from(savedOrder);
    }

    /**
     * 전액 할인 결제(§11-3) — 결제금액이 0원이면 PG 호출을 생략하고 즉시 결제완료로 전이한다.
     * 감사·취소 경로를 PG 결제와 동일하게 유지하기 위해 내부 결제 레코드는 남긴다(거래키 없음).
     * 결제금액이 남아 있으면 승인 전이라 아무 것도 하지 않는다 — 재고 차감은 승인 후에 시작된다.
     */
    private void settleZeroAmountOrder(Order savedOrder) {
        if (savedOrder.getPayableAmount() != 0L) {
            return;
        }
        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .pgProvider(PaymentProvider.NONE)
                .amount(0L)
                .build());
        payment.approve(null, LocalDateTime.now());

        savedOrder.markPaid();
        // AFTER_COMMIT 시 OrderKafkaEventRelay가 Kafka(order.created) 발행 → 재고 차감 Saga 시작
        applicationEventPublisher.publishEvent(OrderCreatedApplicationEvent.from(savedOrder));
    }
}
