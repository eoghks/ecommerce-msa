package com.ecommerce.product.domain;

/**
 * 상품 판매 상태.
 * ACTIVE: 정상 판매 / BANNED: 관리자 판매 금지 (공개 노출·신규 주문 차단)
 */
public enum ProductStatus {
    ACTIVE,
    BANNED
}
