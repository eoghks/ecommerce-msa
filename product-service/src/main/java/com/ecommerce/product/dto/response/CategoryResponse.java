package com.ecommerce.product.dto.response;

import com.ecommerce.product.domain.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
