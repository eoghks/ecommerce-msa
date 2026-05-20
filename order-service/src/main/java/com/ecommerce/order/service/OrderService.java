package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.event.OrderItemPayload;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventPublisher eventPublisher;

    /**
     * 주문 생성.
     * 1. 각 상품 정보 조회 (가격·상품명 스냅샷)
     * 2. Order + OrderItem 저장 (status=PENDING)
     * 3. OrderCreatedEvent 발행 → Product Service 재고 차감 트리거
     */
    @Transactional
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        // 상품 정보 조회 및 OrderItem 생성
        List<OrderItem> items = request.items().stream()
                .map(itemRequest -> {
                    ProductClient.ProductInfo product =
                            productClient.getProduct(itemRequest.productId());
                    return OrderItem.builder()
                            .productId(product.id())
                            .productName(product.name())
                            .price(product.price())
                            .quantity(itemRequest.quantity())
                            .build();
                })
                .toList();

        long totalPrice = items.stream().mapToLong(OrderItem::subtotal).sum();

        Order order = Order.builder()
                .userId(userId)
                .totalPrice(totalPrice)
                .items(items)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Kafka 이벤트 발행 — Product Service 재고 차감 트리거
        List<OrderItemPayload> payloads = savedOrder.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
                .toList();

        eventPublisher.publishOrderCreated(
                new OrderCreatedEvent(savedOrder.getId(), userId, payloads));

        log.info("주문 생성 완료. orderId={}, userId={}, totalPrice={}",
                savedOrder.getId(), userId, totalPrice);

        return OrderResponse.from(savedOrder);
    }
}
