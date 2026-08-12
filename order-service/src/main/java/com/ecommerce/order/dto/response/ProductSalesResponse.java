package com.ecommerce.order.dto.response;

/**
 * 상품별 매출 응답 (V1.1-7) — 취소된 항목은 제외한 유효 매출 기준 Top N.
 * productName 은 주문 시점 스냅샷이므로 동일 상품에 여러 이름이 있을 수 있어 대표값 하나를 사용한다.
 */
public record ProductSalesResponse(Long productId,
                                   String productName,
                                   long revenue,
                                   long quantity) {
}
