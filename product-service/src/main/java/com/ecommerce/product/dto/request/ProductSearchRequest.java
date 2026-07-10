package com.ecommerce.product.dto.request;

import com.ecommerce.product.domain.SortOption;
import org.springframework.data.domain.Pageable;

public record ProductSearchRequest(
        Long categoryId,
        String keyword,
        Long minPrice,
        Long maxPrice,
        SortOption sort,
        Pageable pageable
) {
}
