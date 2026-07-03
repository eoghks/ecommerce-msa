package com.ecommerce.product.controller;

import com.ecommerce.product.dto.response.WishlistItemResponse;
import com.ecommerce.product.service.WishlistService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WishlistService wishlistService;

    /** 찜 추가(멱등) — userId 없으면 401 */
    @PostMapping("/{productId}")
    public ResponseEntity<Void> add(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId
    ) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        wishlistService.addWishlist(userId, productId);
        return ResponseEntity.noContent().build();
    }

    /** 찜 해제(멱등) — userId 없으면 401 */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId
    ) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        wishlistService.removeWishlist(userId, productId);
        return ResponseEntity.noContent().build();
    }

    /** 내 찜 목록(페이징) — userId 없으면 401 */
    @GetMapping("/me")
    public ResponseEntity<Page<WishlistItemResponse>> getMyWishlist(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(wishlistService.getMyWishlist(userId, pageable));
    }

    /** 내 찜 상품 ID 집합(하트 표시용) — userId 없으면 401 */
    @GetMapping("/me/ids")
    public ResponseEntity<Set<Long>> getMyWishlistIds(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(wishlistService.getMyWishlistProductIds(userId));
    }
}
