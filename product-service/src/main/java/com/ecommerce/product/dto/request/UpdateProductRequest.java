package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
        String name,

        @Size(max = 1000, message = "상품 설명은 1000자 이하여야 합니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 1, message = "가격은 1원 이상이어야 합니다.")
        Long price,

        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        int stock,

        String imageUrl,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId
) {
}
