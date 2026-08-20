package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.PaymentConfirmRequest;
import com.ecommerce.order.dto.response.PaymentResponse;
import com.ecommerce.order.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API (V1.1-6).
 * 인증 정보(X-User-Id)는 required=false 로 받아 누락 시 401(UnauthorizedException)로 응답한다
 * — 신규 API 규약(OrderController.confirmPurchase M-5 주석 참조).
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 승인 — 결제위젯 성공 후 (paymentKey, orderId, amount)로 최종 승인한다.
     * 본인 주문만 가능(타인 404), 금액 불일치 400, 재승인 409, PG 승인 실패 400.
     */
    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirm(userId, request));
    }

    /** 주문 결제 상태 조회 — 본인 결제만. 없거나 타인 것이면 404 */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrder(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(paymentService.getByOrder(orderId, userId));
    }
}
