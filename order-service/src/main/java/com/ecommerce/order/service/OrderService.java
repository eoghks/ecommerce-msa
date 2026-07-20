package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.Address;
import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.FailedOrderLog;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.response.FailedOrderResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderItemCancelledApplicationEvent;
import com.ecommerce.order.event.OrderItemCancelledEvent;
import com.ecommerce.order.exception.DeliveryStatusAccessDeniedException;
import com.ecommerce.order.exception.InvalidOrderShippingException;
import com.ecommerce.order.exception.OrderItemAccessDeniedException;
import com.ecommerce.order.exception.OrderItemNotFoundException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.AddressRepository;
import com.ecommerce.order.repository.FailedOrderLogRepository;
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

    /** 재고 확보 실패 자동취소 시 남기는 일반화된 사유 (민감정보 노출 방지) */
    private static final String AUTO_CANCEL_REASON = "재고 확보 실패(자동취소)";

    /** 권한 판정용 역할 코드 */
    private static final String ROLE_ADMIN  = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";

    private final OrderRepository            orderRepository;
    private final ProductClient              productClient;
    private final OrderPersistenceService    orderPersistenceService;
    private final ApplicationEventPublisher  applicationEventPublisher;
    private final FailedOrderLogRepository   failedOrderLogRepository;
    private final AddressRepository          addressRepository;
    private final NotificationService        notificationService;

    /**
     * 주문 생성.
     * 1. 상품 정보 조회 — @Transactional 외부에서 HTTP 호출 (DB 커넥션 점유 최소화)
     * 2. Order + OrderItem 저장 (status=PENDING)
     * 3. ApplicationEvent 등록 → AFTER_COMMIT 시 Kafka 발행 (C-01 수정)
     */
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        // V1.1-3: 배송지 스냅샷 확정 — 주소록 선택(addressId) 또는 직접입력
        ShippingInfo shipping = resolveShipping(userId, request);

        // @Transactional 외부에서 상품 조회 — 외부 HTTP 호출 중 DB 커넥션 점유 방지 (M-04)
        List<OrderItem> items = fetchOrderItems(request);
        long totalPrice = items.stream().mapToLong(OrderItem::subtotal).sum();

        // 별도 빈 호출 → 프록시 경유로 @Transactional 정상 적용 → AFTER_COMMIT 이벤트 발행 보장
        return orderPersistenceService.saveAndPublish(userId, totalPrice, items, shipping);
    }

    /**
     * 배송지 스냅샷 확정.
     * addressId 지정 시 본인 소유 주소를 스냅샷으로 복사(무효 시 400),
     * 미지정 시 직접입력(receiver/phone/address) 필수(누락 시 400).
     */
    private ShippingInfo resolveShipping(Long userId, OrderCreateRequest request) {
        if (request.addressId() != null) {
            Address address = addressRepository.findById(request.addressId())
                    .filter(a -> a.isOwnedBy(userId))
                    .orElseThrow(() -> new InvalidOrderShippingException(
                            "유효하지 않은 배송지입니다. addressId=" + request.addressId()));
            return new ShippingInfo(address.getReceiver(), address.getPhone(), address.getAddress());
        }
        if (isBlank(request.receiver()) || isBlank(request.phone()) || isBlank(request.address())) {
            throw new InvalidOrderShippingException(
                    "배송지를 직접 입력하거나 저장된 배송지를 선택해주세요.");
        }
        return new ShippingInfo(request.receiver(), request.phone(), request.address());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
                            .sellerId(product.sellerId())
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
        // 실제 PENDING→CONFIRMED 전이가 일어난 경우에만 알림 생성(멱등 재전달 시 중복 방지)
        boolean wasPending = order.getStatus() == com.ecommerce.order.domain.OrderStatus.PENDING;
        order.confirm();
        if (wasPending) {
            notificationService.create(order.getUserId(), NotificationType.ORDER_CONFIRMED, orderId);
        }
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
        boolean alreadyCancelled = order.getStatus() == com.ecommerce.order.domain.OrderStatus.CANCELLED;
        order.cancel();
        // M-3: 실제로 취소 전이가 일어난 경우에만 실패 로그 기록 (멱등 — 중복 재전달 시 중복 기록 방지)
        if (!alreadyCancelled) {
            failedOrderLogRepository.save(FailedOrderLog.builder()
                    .orderId(orderId)
                    .userId(order.getUserId())
                    .reason(AUTO_CANCEL_REASON)   // 일반화된 사유만 기록 (원본 reason 미노출)
                    .build());
            notificationService.create(order.getUserId(), NotificationType.ORDER_CANCELLED, orderId);
        }
        log.warn("주문 자동 취소 처리. orderId={}, reason={}", orderId, reason);
    }

    /** 실패(자동취소) 주문 목록 조회 (ADMIN) — M-3 */
    @Transactional(readOnly = true)
    public Page<FailedOrderResponse> getFailedOrders(Pageable pageable) {
        return failedOrderLogRepository.findAllByOrderByOccurredAtDesc(pageable)
                .map(FailedOrderResponse::from);
    }

    /** 내 주문 목록 조회 (페이징) */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::from);
    }

    /** 전체 주문 목록 조회 (ADMIN) */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(OrderResponse::from);
    }

    /** 판매자 주문 목록 조회 (SELLER) — 본인 상품 항목만 노출 */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getSellerOrders(Long sellerId, Pageable pageable) {
        return orderRepository.findBySellerId(sellerId, pageable)
                .map(order -> OrderResponse.forSeller(order, sellerId));
    }

    /**
     * 주문 항목 취소 (사유 필수).
     * ADMIN: 전체 항목 / SELLER: 본인 상품 항목만.
     * 취소 후 주문 상태(전체→CANCELLED, 일부→PARTIALLY_CANCELLED)·합계 재계산.
     * C-2: 재고가 실제 차감된 주문(CONFIRMED/부분취소)만 허용 — 과복구 방지.
     * C-3: 실제 취소 전이가 일어난 경우에만 재고 복구 이벤트 발행 — 중복 발행 방지.
     */
    @Transactional
    public void cancelOrderItem(Long orderId, Long itemId, String reason, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));

        // SELLER는 본인 상품 항목만 취소 가능
        if ("SELLER".equals(role) && !item.isOwnedBy(userId)) {
            throw new OrderItemAccessDeniedException(itemId);
        }

        // C-2: 차감이 완료된 주문만 항목 취소 허용 (과복구 방지)
        if (!order.isItemCancellable()) {
            throw new IllegalStateException(
                    "항목을 취소할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus());
        }

        // C-3: 실제 ACTIVE→CANCELLED 전이가 일어난 경우에만 복구 이벤트 발행
        order.cancelItem(itemId, reason).ifPresent(cancelled -> {
            log.info("주문 항목 취소. orderId={}, itemId={}, by={}({}), reason={}",
                    orderId, itemId, userId, role, reason);
            applicationEventPublisher.publishEvent(new OrderItemCancelledApplicationEvent(
                    new OrderItemCancelledEvent(
                            orderId, cancelled.getId(), cancelled.getProductId(), cancelled.getQuantity())));
            notificationService.create(order.getUserId(), NotificationType.ORDER_ITEM_CANCELLED, orderId);
        });
    }

    /**
     * V1.1-3: 배송상태 변경. PREPARING→SHIPPING→DELIVERED 전진만(전이 검증은 도메인).
     * 권한: ADMIN 전체 / SELLER는 주문에 본인 상품이 포함된 경우만. 그 외 403.
     */
    @Transactional
    public OrderResponse updateDeliveryStatus(Long orderId, DeliveryStatus next, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!canManageDelivery(order, userId, role)) {
            throw new DeliveryStatusAccessDeniedException(orderId);
        }

        order.advanceDeliveryStatus(next);
        // V1.1-4: 배송상태 전이에 맞춰 배송 알림 생성(준비중 등 대상 아님이면 미생성)
        NotificationType.fromDeliveryStatus(next).ifPresent(type ->
                notificationService.create(order.getUserId(), type, orderId));
        log.info("배송상태 변경. orderId={}, next={}, by={}({})", orderId, next, userId, role);
        return OrderResponse.from(order);
    }

    /** 배송상태 변경 권한 판정 — ADMIN 전체, SELLER는 본인 상품 포함 주문만 */
    private boolean canManageDelivery(Order order, Long userId, String role) {
        if (ROLE_ADMIN.equals(role)) {
            return true;
        }
        return ROLE_SELLER.equals(role) && order.hasSellerItem(userId);
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

    /** 사용자 주문 취소 기본 사유 */
    private static final String USER_CANCEL_REASON = "고객 주문 취소";

    /**
     * 사용자 주문 취소 요청 — PENDING/CONFIRMED/PARTIALLY_CANCELLED 상태 취소 가능 (M-N3).
     * 멱등 처리(E2E S5): 이미 전체 취소(CANCELLED)된 주문 재취소 요청은 no-op으로 성공 처리.
     *   - 활성 항목이 없으므로 재고 복구 이벤트를 재발행하지 않는다 (중복 복구 방지).
     * 설계: 차감된 주문은 활성 항목 전체를 항목취소 처리하여 기존 재고복구 Saga(항목취소 이벤트)를 재사용.
     *   - 실제 전이가 일어난 항목만 복구 이벤트 발행 → 중복/과복구 방지
     *   - PENDING(미차감)은 항목취소 없이 단순 취소 → 복구 이벤트 없음
     * @param reason 취소 사유 (없으면 기본 "고객 주문 취소")
     */
    @Transactional
    public void cancelByUser(Long orderId, Long userId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        // 멱등: 이미 전체 취소된 주문은 아무 작업 없이 성공(no-op) — 복구 이벤트 미발행
        if (order.isFullyCancelled()) {
            log.info("이미 취소된 주문 재취소 요청 — 멱등 no-op. orderId={}, userId={}", orderId, userId);
            return;
        }

        String cancelReason = (reason == null || reason.isBlank()) ? USER_CANCEL_REASON : reason;
        List<OrderItem> restockTargets = order.cancelByUser(cancelReason);
        restockTargets.forEach(item -> applicationEventPublisher.publishEvent(
                new OrderItemCancelledApplicationEvent(
                        new OrderItemCancelledEvent(
                                orderId, item.getId(), item.getProductId(), item.getQuantity()))));

        notificationService.create(order.getUserId(), NotificationType.ORDER_CANCELLED, orderId);
        log.info("사용자 주문 취소. orderId={}, userId={}, 복구항목수={}",
                orderId, userId, restockTargets.size());
    }
}
