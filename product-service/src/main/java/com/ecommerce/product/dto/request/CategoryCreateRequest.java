package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

        @NotBlank(message = "카테고리명은 필수입니다.")
        @Size(max = 50, message = "카테고리명은 50자 이하여야 합니다.")
        String name

) {}
