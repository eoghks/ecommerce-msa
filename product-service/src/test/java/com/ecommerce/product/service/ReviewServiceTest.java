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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductService productService;
    @Mock private OrderClient orderClient;

    @InjectMocks private ReviewService reviewService;

    private static final Long PRODUCT_ID = 10L;
    private static final Long USER_ID = 1L;

    // ── 작성 ──────────────────────────────────────────────

    @Test
    @DisplayName("구매자 작성 성공 — 저장 후 평균 재계산 + 캐시 무효화")
    void createReview_success() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(true);
        given(orderClient.hasPurchased(USER_ID, PRODUCT_ID)).willReturn(true);
        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.createReview(
                PRODUCT_ID, USER_ID, new CreateReviewRequest(5, "좋아요"));

        assertThat(response.rating()).isEqualTo(5);
        verify(reviewRepository).recalculateRating(PRODUCT_ID);
        verify(productService).evictProductDetailCache(PRODUCT_ID);
    }

    @Test
    @DisplayName("미인증(userId null) — 401")
    void createReview_unauthenticated() {
        assertThatThrownBy(() -> reviewService.createReview(
                PRODUCT_ID, null, new CreateReviewRequest(5, "좋아요")))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품 — 404")
    void createReview_productNotFound() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(
                PRODUCT_ID, USER_ID, new CreateReviewRequest(5, "좋아요")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("비구매자 작성 차단 — 403")
    void createReview_notPurchased() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(true);
        given(orderClient.hasPurchased(USER_ID, PRODUCT_ID)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(
                PRODUCT_ID, USER_ID, new CreateReviewRequest(5, "좋아요")))
                .isInstanceOf(PurchaseRequiredException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("내부호출 실패 시 거부 — OrderClient false면 403")
    void createReview_internalCallFails() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(true);
        given(orderClient.hasPurchased(USER_ID, PRODUCT_ID)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(
                PRODUCT_ID, USER_ID, new CreateReviewRequest(4, "보통")))
                .isInstanceOf(PurchaseRequiredException.class);
    }

    @Test
    @DisplayName("중복 작성 — 409")
    void createReview_duplicate() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(true);
        given(orderClient.hasPurchased(USER_ID, PRODUCT_ID)).willReturn(true);
        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(
                PRODUCT_ID, USER_ID, new CreateReviewRequest(5, "좋아요")))
                .isInstanceOf(DuplicateReviewException.class);
        verify(reviewRepository, never()).save(any());
    }

    // ── 수정 ──────────────────────────────────────────────

    @Test
    @DisplayName("본인 수정 성공 — 평균 재계산 + 캐시 무효화")
    void updateReview_owner() {
        Review review = buildReview(USER_ID);
        given(reviewRepository.findById(100L)).willReturn(Optional.of(review));

        ReviewResponse response = reviewService.updateReview(
                PRODUCT_ID, 100L, USER_ID, new UpdateReviewRequest(3, "수정함"));

        assertThat(response.rating()).isEqualTo(3);
        verify(reviewRepository).recalculateRating(PRODUCT_ID);
        verify(productService).evictProductDetailCache(PRODUCT_ID);
    }

    @Test
    @DisplayName("타인 수정 차단 — 403")
    void updateReview_notOwner() {
        Review review = buildReview(USER_ID);
        given(reviewRepository.findById(100L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(
                PRODUCT_ID, 100L, 999L, new UpdateReviewRequest(3, "수정함")))
                .isInstanceOf(NotReviewOwnerException.class);
    }

    @Test
    @DisplayName("없는 리뷰 수정 — 404")
    void updateReview_notFound() {
        given(reviewRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(
                PRODUCT_ID, 100L, USER_ID, new UpdateReviewRequest(3, "수정함")))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    // ── 삭제 ──────────────────────────────────────────────

    @Test
    @DisplayName("본인 삭제 성공")
    void deleteReview_owner() {
        Review review = buildReview(USER_ID);
        given(reviewRepository.findById(100L)).willReturn(Optional.of(review));

        reviewService.deleteReview(PRODUCT_ID, 100L, USER_ID, "USER");

        verify(reviewRepository).delete(review);
        verify(reviewRepository).recalculateRating(PRODUCT_ID);
        verify(productService).evictProductDetailCache(PRODUCT_ID);
    }

    @Test
    @DisplayName("ADMIN 타인 리뷰 삭제 성공")
    void deleteReview_admin() {
        Review review = buildReview(USER_ID);
        given(reviewRepository.findById(100L)).willReturn(Optional.of(review));

        reviewService.deleteReview(PRODUCT_ID, 100L, 999L, "ADMIN");

        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("타인(비ADMIN) 삭제 차단 — 403")
    void deleteReview_notOwner() {
        Review review = buildReview(USER_ID);
        given(reviewRepository.findById(100L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(PRODUCT_ID, 100L, 999L, "USER"))
                .isInstanceOf(NotReviewOwnerException.class);
        verify(reviewRepository, never()).delete(any());
    }

    private Review buildReview(Long ownerId) {
        Review review = Review.builder()
                .productId(PRODUCT_ID)
                .userId(ownerId)
                .rating(5)
                .content("좋아요")
                .build();
        ReflectionTestUtils.setField(review, "id", 100L);
        return review;
    }
}
