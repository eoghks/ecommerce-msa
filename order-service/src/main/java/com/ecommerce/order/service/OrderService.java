package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderItemPayload;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository        orderRepository;
    private final ProductClient          productClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 주문 생성.
     * 1. 상품 정보 조회 — @Transactional 외부에서 HTTP 호출 (DB 커넥션 점유 최소화)
     * 2. Order + OrderItem 저장 (status=PENDING)
     * 3. ApplicationEvent 등록 → AFTER_COMMIT 시 Kafka 발행 (C-01 수정)
     */
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        // @Transactional 외부에서 상품 조회 — 외부 HTTP 호출 중 DB 커넥션 점유 방지 (M-04)
        List<OrderItem> items = fetchOrderItems(request);
        long totalPrice = items.stream().mapToLong(OrderItem::subtotal).sum();

        return saveOrderAndPublishEvent(userId, totalPrice, items);
    }

    /**
     * 주문 저장 + ApplicationEvent 등록.
     * @Transactional 범위를 DB 작업으로만 한정.
     */
    @Transactional
    protected OrderResponse saveOrderAndPublishEvent(Long userId, long totalPrice,
                                                     List<OrderItem> items) {
        Order order = Order.builder()
                .userId(userId)
                .totalPrice(totalPrice)
                .items(items)
                .build();
        Order savedOrder = orderRepository.save(order);

        // ApplicationEvent 등록 — AFTER_COMMIT 시 OrderKafkaEventRelay가 Kafka 발행
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

    /** 상품 정보 조회 및 OrderItem 생성 — 트랜잭션 외부 실행 */
    private List<OrderItem> fetchOrderItems(OrderCreateRequest request) {
        return request.items().stream()
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
    }

    /**
     * 주문 확정 — stock.decreased 이벤트 수신 시 호출 (Saga).
     * 멱등 처리: 이미 CONFIRMED 이면 skip (at-least-once 재전달 대응)
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
     * 멱등 처리: 이미 CANCELLED 이면 skip
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
