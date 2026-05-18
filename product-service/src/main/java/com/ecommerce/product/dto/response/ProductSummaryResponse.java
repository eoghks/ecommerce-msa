package com.ecommerce.product.dto.response;

import com.ecommerce.product.domain.Product;

public record ProductSummaryResponse(
        Long id,
        String name,
        Long price,
        int stock,
        String imageUrl,
        Long categoryId,
        String categoryName
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getCategory().getId(),
                product.getCategory().getName()
        );
    }
}
