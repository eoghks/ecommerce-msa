package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 장바구니 추가 요청 — 가격·상품명은 서버에서 Product Service를 통해 검증·조회 */
public record CartAddRequest(
        @NotNull Long productId,
        @Min(1) int quantity
) {}
