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
    public static ProductSummaryResponse from(Product product) {
        return from(product, null);
    }

    /** seller 정보를 함께 매핑 (ADMIN 화면). seller가 null이면 판매자명/이메일은 비움 */
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
