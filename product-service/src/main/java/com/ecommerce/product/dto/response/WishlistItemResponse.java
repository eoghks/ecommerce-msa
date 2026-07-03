package com.ecommerce.product.dto.response;

import com.ecommerce.product.domain.ProductStatus;

import java.time.LocalDateTime;

/**
 * 내 찜 목록 항목 — 찜 정보 + 상품 요약.
 * status는 사용자 표시용 라벨("판매중"/"판매중지")로 변환해 담는다.
 */
public record WishlistItemResponse(
        Long productId,
        String name,
        Long price,
        String imageUrl,
        String status,
        LocalDateTime createdAt
) {
    private static final String STATUS_ON_SALE = "판매중";
    private static final String STATUS_BANNED  = "판매중지";

    /**
     * 찜-상품 조인 결과로 항목을 생성한다.
     * @param productStatus 상품 판매 상태(BANNED면 "판매중지" 라벨)
     */
    public static WishlistItemResponse of(Long productId, String name, Long price,
                                          String imageUrl, ProductStatus productStatus,
                                          LocalDateTime createdAt) {
        String label = productStatus == ProductStatus.BANNED ? STATUS_BANNED : STATUS_ON_SALE;
        return new WishlistItemResponse(productId, name, price, imageUrl, label, createdAt);
    }
}
