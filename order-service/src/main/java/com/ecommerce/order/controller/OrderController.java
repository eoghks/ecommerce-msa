package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.DeliveryStatusUpdateRequest;
import com.ecommerce.order.dto.request.OrderCancelRequest;
import com.ecommerce.order.dto.request.OrderCreateRequest;
import com.ecommerce.order.dto.request.OrderItemCancelRequest;
import com.ecommerce.order.dto.response.FailedOrderResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.dto.response.SellerOrderResponse;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PurchaseConfirmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService           orderService;
    private final PurchaseConfirmService purchaseConfirmService;

    /** 주문 생성 */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(userId, request));
    }

    /** 내 주문 목록 조회 (페이징, 기본 20건) */
    @GetMapping("/me")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getMyOrders(userId, pageable));
    }

    /** 전체 주문 목록 조회 (ADMIN) */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    /** 실패(자동취소) 주문 목록 조회 (ADMIN) — M-3 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/failed")
    public ResponseEntity<Page<FailedOrderResponse>> getFailedOrders(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getFailedOrders(pageable));
    }

    /** 판매자 주문 목록 조회 (SELLER) — 본인 상품 항목·본인 항목 합계(sellerItemsTotal)만 노출 (M-6) */
    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/seller")
    public ResponseEntity<Page<SellerOrderResponse>> getSellerOrders(
            @RequestHeader("X-User-Id") Long sellerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getSellerOrders(sellerId, pageable));
    }

    /** 주문 상세 조회 */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.getOrder(orderId, userId));
    }

    /**
     * 주문 취소 (PENDING/CONFIRMED/부분취소 가능 — M-N3).
     * 사유는 선택 — 미입력 시 기본 "고객 주문 취소". 차감된 주문은 재고 복구 Saga 재사용.
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) OrderCancelRequest request
    ) {
        String reason = (request == null) ? null : request.reason();
        orderService.cancelByUser(orderId, userId, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * 주문 항목 취소 (ADMIN: 전체 / SELLER: 본인 상품 항목). 사유 필수.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PatchMapping("/{orderId}/items/{itemId}/cancel")
    public ResponseEntity<Void> cancelOrderItem(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody OrderItemCancelRequest request
    ) {
        orderService.cancelOrderItem(orderId, itemId, request.reason(), userId, role);
        return ResponseEntity.noContent().build();
    }

    /**
     * 구매확정 (주문 소유자 본인). 확정 대상 주문상태 + 배송완료(DELIVERED) + 미확정 주문만 가능.
     * 진행 중 반품이 있으면 확정할 수 없다. 확정 후에는 반품 자격이 사라진다 (payment-foundation §3.2).
     * 자격 미충족 400, 이미 확정 409, 타인 주문 404, 인증 정보 부재 401.
     *
     * M-5: 이 엔드포인트만 X-User-Id 를 required=false 로 받는 이유 —
     * 헤더 누락을 스프링의 400(Bad Request)이 아니라 서비스의 401(UnauthorizedException)로 응답하는
     * 반품 API(ReturnController) 규약을 따른다. 인증 부재는 요청 형식 오류가 아니라 인증 오류이므로
     * 신규 API 는 이 규약을 사용한다(기존 주문 API 의 필수 헤더 방식은 호환을 위해 유지).
     */
    @PatchMapping("/{orderId}/purchase-confirm")
    public ResponseEntity<OrderResponse> confirmPurchase(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(purchaseConfirmService.confirm(orderId, userId));
    }

    /**
     * 배송상태 변경 (ADMIN 전체 / SELLER 본인 상품 포함 주문). PREPARING→SHIPPING→DELIVERED 전진만.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PatchMapping("/{orderId}/delivery-status")
    public ResponseEntity<OrderResponse> updateDeliveryStatus(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long orderId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                orderService.updateDeliveryStatus(orderId, request.status(), userId, role));
    }
}
