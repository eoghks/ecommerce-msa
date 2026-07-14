package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.CreateReviewRequest;
import com.ecommerce.product.dto.request.UpdateReviewRequest;
import com.ecommerce.product.dto.response.ReviewResponse;
import com.ecommerce.product.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 리뷰·별점 (V1.1-1).
 * 작성/수정/삭제는 인증 필요(X-User-Id), 목록은 공개.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 리뷰 작성 — 구매자만, 1인 1리뷰 */
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(productId, userId, request));
    }

    /** 리뷰 수정 (본인) */
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        return ResponseEntity.ok(reviewService.updateReview(productId, reviewId, userId, request));
    }

    /** 리뷰 삭제 (본인 또는 ADMIN) */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long productId,
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(productId, reviewId, userId, role);
        return ResponseEntity.noContent().build();
    }

    /** 리뷰 목록 조회 (공개, 최신순 페이징) */
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PathVariable Long productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.getReviews(productId, pageable));
    }
}
