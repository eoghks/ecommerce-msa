package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(
        @NotNull Long productId,
        @NotBlank String productName,
        @NotNull Long price,
        @Min(1) int quantity,
        String imageUrl
) {}
