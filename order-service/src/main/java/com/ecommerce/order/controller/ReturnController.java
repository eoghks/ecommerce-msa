package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.ReturnCreateRequest;
import com.ecommerce.order.dto.request.ReturnRejectRequest;
import com.ecommerce.order.dto.response.ReturnResponse;
import com.ecommerce.order.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 반품·환불 API (V1.1-5).
 * 신청은 주문 하위 경로(/api/v1/orders/...), 조회·처리는 /api/v1/returns 경로를 사용한다.
 */
@RestController
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    /** 반품 신청 (주문 소유자) — 사유 필수 */
    @PostMapping("/api/v1/orders/{orderId}/items/{itemId}/returns")
    public ResponseEntity<ReturnResponse> requestReturn(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody ReturnCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnService.request(orderId, itemId, userId, request.reason()));
    }

    /** 내 반품 목록 (페이징, 최신순) */
    @GetMapping("/api/v1/returns/me")
    public ResponseEntity<Page<ReturnResponse>> getMyReturns(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(returnService.getMyReturns(userId, pageable));
    }

    /** 반품 관리 목록 (ADMIN 전체 / SELLER 본인 상품 포함 건만) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @GetMapping("/api/v1/returns/admin")
    public ResponseEntity<Page<ReturnResponse>> getManagedReturns(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(returnService.getManagedReturns(userId, role, pageable));
    }

    /** 반품 승인 (ADMIN / 해당 SELLER) — 재고 복구 + 환불 처리 */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PatchMapping("/api/v1/returns/{returnId}/approve")
    public ResponseEntity<ReturnResponse> approveReturn(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long returnId
    ) {
        return ResponseEntity.ok(returnService.approve(returnId, userId, role));
    }

    /** 반품 거부 (ADMIN / 해당 SELLER) — 거부 사유 필수 */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PatchMapping("/api/v1/returns/{returnId}/reject")
    public ResponseEntity<ReturnResponse> rejectReturn(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long returnId,
            @Valid @RequestBody ReturnRejectRequest request
    ) {
        return ResponseEntity.ok(
                returnService.reject(returnId, userId, role, request.rejectReason()));
    }
}
