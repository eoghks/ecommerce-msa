package com.ecommerce.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 리뷰 수정 요청 (V1.1-1) */
public record UpdateReviewRequest(

        @NotNull(message = "별점은 필수입니다.")
        @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "별점은 5 이하여야 합니다.")
        Integer rating,

        @Size(max = 1000, message = "리뷰 내용은 1000자 이하여야 합니다.")
        String content
) {
}
