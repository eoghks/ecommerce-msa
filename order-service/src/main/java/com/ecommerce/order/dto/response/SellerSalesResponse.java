package com.ecommerce.order.dto.response;

/**
 * 판매자별 매출 응답 (V1.1-7) — 취소된 항목은 제외한 유효 매출 기준 Top N.
 * sellerId 가 null 이면 판매자 없이 등록된 플랫폼(ADMIN) 상품이다.
 */
public record SellerSalesResponse(Long sellerId,
                                  long revenue,
                                  long quantity,
                                  long orderCount) {
}
