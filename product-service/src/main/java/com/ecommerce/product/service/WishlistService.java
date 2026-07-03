package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.Wishlist;
import com.ecommerce.product.dto.response.WishlistItemResponse;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.exception.ProductNotOnSaleException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;

    /**
     * 찜 추가(멱등) — 판매중 상품만 허용.
     * 상품이 없으면 404, 판매금지면 400. 이미 찜한 상품이면 그대로 둔다.
     */
    @Transactional
    public void addWishlist(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.isBanned()) {
            throw new ProductNotOnSaleException(productId);
        }
        // 이미 찜한 경우 멱등 처리(유니크 제약이 최종 방어)
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        wishlistRepository.save(Wishlist.builder()
                .userId(userId)
                .productId(productId)
                .build());
    }

    /** 찜 해제(멱등) — 본인 소유 찜만 삭제. 없어도 조용히 성공. */
    @Transactional
    public void removeWishlist(Long userId, Long productId) {
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    /** 내 찜 목록(페이징) — 상품 조인으로 한 번에 조회(N+1 방지). */
    @Transactional(readOnly = true)
    public Page<WishlistItemResponse> getMyWishlist(Long userId, Pageable pageable) {
        return wishlistRepository.findItemsByUserId(userId, pageable)
                .map(p -> WishlistItemResponse.of(
                        p.productId(), p.name(), p.price(),
                        p.imageUrl(), p.status(), p.createdAt()));
    }

    /** 내 찜 상품 ID 집합(하트 표시용). */
    @Transactional(readOnly = true)
    public Set<Long> getMyWishlistProductIds(Long userId) {
        List<Long> ids = wishlistRepository.findProductIdsByUserId(userId);
        return Set.copyOf(ids);
    }
}
