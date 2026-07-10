package com.ecommerce.product.service;

import com.ecommerce.product.client.OrderClient;
import com.ecommerce.product.domain.Review;
import com.ecommerce.product.dto.request.CreateReviewRequest;
import com.ecommerce.product.dto.request.UpdateReviewRequest;
import com.ecommerce.product.dto.response.ReviewResponse;
import com.ecommerce.product.exception.DuplicateReviewException;
import com.ecommerce.product.exception.NotReviewOwnerException;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.exception.PurchaseRequiredException;
import com.ecommerce.product.exception.ReviewNotFoundException;
import com.ecommerce.product.exception.UnauthenticatedException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** ADMIN은 타인 리뷰 삭제 가능 */
    private static final String ROLE_ADMIN = "ADMIN";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final OrderClient orderClient;

    /** 리뷰 작성 — 구매 인증 + 1인1리뷰. 저장 후 평균 재계산 + 캐시 무효화 */
    @Transactional
    public ReviewResponse createReview(Long productId, Long userId, CreateReviewRequest request) {
        requireUser(userId);
        requireProductExists(productId);
        if (!orderClient.hasPurchased(userId, productId)) {
            throw new PurchaseRequiredException(productId);
        }
        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new DuplicateReviewException(productId);
        }
        Review review = reviewRepository.save(Review.builder()
                .productId(productId)
                .userId(userId)
                .rating(request.rating())
                .content(sanitize(request.content()))
                .build());
        refreshRating(productId);
        return ReviewResponse.from(review);
    }

    /** 리뷰 수정 — 본인만. 갱신 후 평균 재계산 + 캐시 무효화 */
    @Transactional
    public ReviewResponse updateReview(Long productId, Long reviewId, Long userId, UpdateReviewRequest request) {
        requireUser(userId);
        Review review = loadReview(reviewId);
        if (!review.isOwnedBy(userId)) {
            throw new NotReviewOwnerException(reviewId);
        }
        review.update(request.rating(), sanitize(request.content()));
        refreshRating(productId);
        return ReviewResponse.from(review);
    }

    /** 리뷰 삭제 — 본인 또는 ADMIN. 삭제 후 평균 재계산 + 캐시 무효화 */
    @Transactional
    public void deleteReview(Long productId, Long reviewId, Long userId, String role) {
        requireUser(userId);
        Review review = loadReview(reviewId);
        if (!review.isOwnedBy(userId) && !ROLE_ADMIN.equals(role)) {
            throw new NotReviewOwnerException(reviewId);
        }
        reviewRepository.delete(review);
        refreshRating(productId);
    }

    /** 리뷰 목록 조회 (공개, 최신순 페이징) */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(ReviewResponse::from);
    }

    // ── private helpers ──────────────────────────────────────────

    /** 평균 별점 재계산 UPDATE 후 상세 캐시 무효화 */
    private void refreshRating(Long productId) {
        reviewRepository.recalculateRating(productId);
        productService.evictProductDetailCache(productId);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthenticatedException();
        }
    }

    private void requireProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
    }

    private Review loadReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    }

    /** XSS 방지 — 내용 HTML 이스케이프. null이면 그대로 null */
    private String sanitize(String content) {
        return content == null ? null : HtmlUtils.htmlEscape(content);
    }
}
