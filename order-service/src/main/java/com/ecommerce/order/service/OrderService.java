package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderEventPublisher;
import com.ecommerce.order.event.OrderItemPayload;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 주문 확정 — stock.decreased 이벤트 수신 시 호출 (Saga 보상 완료).
     * PENDING → CONFIRMED
     */
    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.confirm();
        log.info("주문 확정 완료. orderId={}", orderId);
    }

    /**
     * 주문 취소 — stock.decrease.failed 이벤트 수신 시 호출 (Saga 보상 트랜잭션).
     * PENDING → CANCELLED
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel();
        log.warn("주문 취소 처리. orderId={}, reason={}", orderId, reason);
    }

    /** 내 주문 목록 조회 (페이징) */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::from);
    }

    /** 주문 상세 조회 — 본인 주문만 허용 */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);  // 타인 주문은 404 처리 (정보 노출 방지)
        }
        return OrderResponse.from(order);
    }

    /**
     * 사용자 주문 취소 요청 — PENDING 상태만 취소 가능.
     * CONFIRMED / CANCELLED 상태에서 시도 시 409 Conflict.
     */
    @Transactional
    public void cancelByUser(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (!order.isCancellable()) {
            throw new IllegalStateException(
                    "취소할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus());
        }
        order.cancel();
        log.info("사용자 주문 취소. orderId={}, userId={}", orderId, userId);
    }
}
