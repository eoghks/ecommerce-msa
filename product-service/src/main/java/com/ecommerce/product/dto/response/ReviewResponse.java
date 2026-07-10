package com.ecommerce.product.dto.response;

import com.ecommerce.product.domain.Review;

import java.time.LocalDateTime;

/** 리뷰 응답 (V1.1-1) */
public record ReviewResponse(
        Long reviewId,
        Long productId,
        Long userId,
        int rating,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
