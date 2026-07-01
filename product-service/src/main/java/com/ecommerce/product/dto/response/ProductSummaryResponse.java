package com.ecommerce.product.dto.response;

import com.ecommerce.product.client.UserClient;
import com.ecommerce.product.domain.Product;

public record ProductSummaryResponse(
        Long id,
        String name,
        Long price,
        int stock,
        String imageUrl,
        Long sellerId,
        String sellerName,   // ADMIN 조회 시에만 채워짐 (그 외 null)
        String sellerEmail,  // ADMIN 조회 시에만 채워짐 (그 외 null)
        String status,
        Long categoryId,
        String categoryName
) {
    /** 공개/일반 목록 — M-4: 판매자 식별자(sellerId)·정보 노출 안 함 */
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                null,   // sellerId 비노출
                null,
                null,
                product.getStatus().name(),
                product.getCategory().getId(),
                product.getCategory().getName()
        );
    }

    /** ADMIN 화면 — 판매자 식별자·이름·이메일 포함. seller가 null이면 이름/이메일은 비움 */
    public static ProductSummaryResponse from(Product product, UserClient.UserSummary seller) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getSellerId(),
                seller != null ? seller.name()  : null,
                seller != null ? seller.email() : null,
                product.getStatus().name(),
                product.getCategory().getId(),
                product.getCategory().getName()
        );
    }
}
