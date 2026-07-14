package com.ecommerce.order.controller;

import com.ecommerce.order.dto.response.PurchasedResponse;
import com.ecommerce.order.service.InternalOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1.1-1: 서비스 간 직접 호출 전용 내부 엔드포인트.
 * 게이트웨이 화이트리스트에서 제외(INTERNAL_ONLY) — 외부 접근 차단. X-Internal-Token 필수.
 */
@RestController
@RequestMapping("/api/v1/orders/internal")
@RequiredArgsConstructor
public class InternalOrderController {

    private final InternalOrderService internalOrderService;

    /** 구매 인증 — 해당 사용자가 상품을 실제 구매했는지 조회 (리뷰 작성 자격 판정) */
    @GetMapping("/purchased")
    public ResponseEntity<PurchasedResponse> isPurchased(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestParam Long userId,
            @RequestParam Long productId
    ) {
        internalOrderService.verifyInternalToken(internalToken);
        boolean purchased = internalOrderService.isPurchased(userId, productId);
        return ResponseEntity.ok(new PurchasedResponse(purchased));
    }
}
