package com.ecommerce.product.dto.response;

import com.ecommerce.product.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        Long price,
        int stock,
        String imageUrl,
        Long sellerId,
        String status,
        Long categoryId,
        String categoryName,
        BigDecimal ratingAvg,
        int ratingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getSellerId(),
                product.getStatus().name(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getRatingAvg(),
                product.getRatingCount(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
