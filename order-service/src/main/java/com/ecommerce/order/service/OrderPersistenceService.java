package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderItemPayload;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
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

        // AFTER_COMMIT 시 OrderKafkaEventRelay가 Kafka(order.created) 발행
        List<OrderItemPayload> payloads = savedOrder.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();
        applicationEventPublisher.publishEvent(
                new OrderCreatedApplicationEvent(
                        new OrderCreatedEvent(savedOrder.getId(), userId, payloads)));

        log.info("주문 생성 완료. orderId={}, userId={}, totalPrice={}",
                savedOrder.getId(), userId, totalPrice);
        return OrderResponse.from(savedOrder);
    }
}
