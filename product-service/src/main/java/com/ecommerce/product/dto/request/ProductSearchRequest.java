package com.ecommerce.product.dto.request;

import org.springframework.data.domain.Pageable;

public record ProductSearchRequest(
        Long categoryId,
        String keyword,
        Pageable pageable
) {
}
