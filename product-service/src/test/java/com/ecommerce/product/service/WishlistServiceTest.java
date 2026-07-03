package com.ecommerce.product.service;

import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.ProductStatus;
import com.ecommerce.product.dto.response.WishlistItemResponse;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.exception.ProductNotOnSaleException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.WishlistItemProjection;
import com.ecommerce.product.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistService 단위 테스트")
class WishlistServiceTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private WishlistService wishlistService;

    private static final Long USER_ID    = 1L;
    private static final Long PRODUCT_ID = 100L;

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = Category.builder().name("전자기기").build();
        ReflectionTestUtils.setField(category, "id", 1L);

        product = Product.builder()
                .name("테스트 상품")
                .price(10_000L)
                .stock(10)
                .imageUrl("image.jpg")
                .category(category)
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    }

    // ── 찜 추가 ────────────────────────────────────────────────────

    @Test
    @DisplayName("찜 추가 — 신규(판매중 상품) 저장")
    void add_new_success() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(wishlistRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);

        wishlistService.addWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistRepository).save(any());
    }

    @Test
    @DisplayName("찜 추가 — 이미 찜한 상품이면 멱등(저장 안 함)")
    void add_duplicate_idempotent() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(wishlistRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

        wishlistService.addWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 추가 — 없는 상품이면 404")
    void add_productNotFound() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addWishlist(USER_ID, PRODUCT_ID))
                .isInstanceOf(ProductNotFoundException.class);
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 추가 — 판매금지 상품이면 예외(400)")
    void add_bannedProduct() {
        product.ban();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> wishlistService.addWishlist(USER_ID, PRODUCT_ID))
                .isInstanceOf(ProductNotOnSaleException.class);
        verify(wishlistRepository, never()).save(any());
    }

    // ── 찜 해제 ────────────────────────────────────────────────────

    @Test
    @DisplayName("찜 해제 — 존재하는 찜 삭제")
    void remove_existing() {
        given(wishlistRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(1);

        wishlistService.removeWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("찜 해제 — 없어도 멱등(예외 없음)")
    void remove_missing_idempotent() {
        given(wishlistRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(0);

        wishlistService.removeWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("찜 해제 — 본인 것만 삭제(user_id 조건 포함)")
    void remove_onlyOwn() {
        Long otherUserId = 999L;
        given(wishlistRepository.deleteByUserIdAndProductId(otherUserId, PRODUCT_ID)).willReturn(0);

        wishlistService.removeWishlist(otherUserId, PRODUCT_ID);

        // 타인이 요청해도 user_id 조건으로 본인 것이 없어 삭제 0건(멱등)
        verify(wishlistRepository).deleteByUserIdAndProductId(otherUserId, PRODUCT_ID);
    }

    // ── 목록 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("찜 목록 — 페이징 + 판매중 상품 status '판매중'")
    void getMyWishlist_paging_onSale() {
        PageRequest pageable = PageRequest.of(0, 20);
        WishlistItemProjection p = new WishlistItemProjection(
                PRODUCT_ID, "테스트 상품", 10_000L, "image.jpg",
                ProductStatus.ACTIVE, LocalDateTime.now());
        Page<WishlistItemProjection> page = new PageImpl<>(List.of(p), pageable, 1);
        given(wishlistRepository.findItemsByUserId(eq(USER_ID), any())).willReturn(page);

        Page<WishlistItemResponse> result = wishlistService.getMyWishlist(USER_ID, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("판매중");
        assertThat(result.getContent().get(0).productId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("찜 목록 — 판매금지 상품 status '판매중지'로 표기(제거 안 함)")
    void getMyWishlist_bannedStatus() {
        PageRequest pageable = PageRequest.of(0, 20);
        WishlistItemProjection p = new WishlistItemProjection(
                PRODUCT_ID, "테스트 상품", 10_000L, "image.jpg",
                ProductStatus.BANNED, LocalDateTime.now());
        Page<WishlistItemProjection> page = new PageImpl<>(List.of(p), pageable, 1);
        given(wishlistRepository.findItemsByUserId(eq(USER_ID), any())).willReturn(page);

        Page<WishlistItemResponse> result = wishlistService.getMyWishlist(USER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("판매중지");
    }

    // ── ID 집합 ────────────────────────────────────────────────────

    @Test
    @DisplayName("찜 ID 집합 — 상품 ID 반환")
    void getMyWishlistProductIds() {
        given(wishlistRepository.findProductIdsByUserId(USER_ID))
                .willReturn(List.of(100L, 200L, 300L));

        Set<Long> ids = wishlistService.getMyWishlistProductIds(USER_ID);

        assertThat(ids).containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    @Test
    @DisplayName("찜 ID 집합 — 없으면 빈 집합")
    void getMyWishlistProductIds_empty() {
        given(wishlistRepository.findProductIdsByUserId(USER_ID)).willReturn(List.of());

        Set<Long> ids = wishlistService.getMyWishlistProductIds(USER_ID);

        assertThat(ids).isEmpty();
    }
}
