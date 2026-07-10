package com.ecommerce.order.dto.response;

/**
 * V1.1-1: 구매 인증 응답 (product-service 내부 호출).
 * purchased=true 이면 해당 사용자가 상품을 실제 구매(ACTIVE 항목 보유)했음을 뜻한다.
 */
public record PurchasedResponse(boolean purchased) {
}
