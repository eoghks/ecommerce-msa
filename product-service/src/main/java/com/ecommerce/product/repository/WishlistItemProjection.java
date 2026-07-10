package com.ecommerce.product.repository;

import com.ecommerce.product.domain.ProductStatus;

import java.time.LocalDateTime;

/**
 * 찜-상품 조인 조회 결과 투영(N+1 방지용).
 * 물리 삭제된 상품은 조인에서 제외되어 이 투영에 나타나지 않는다.
 */
public record WishlistItemProjection(
        Long productId,
        String name,
        Long price,
        String imageUrl,
        ProductStatus status,
        LocalDateTime createdAt
) {
}
