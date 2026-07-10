package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 상품별 리뷰 목록 (최신순 페이징) */
    Page<Review> findByProductId(Long productId, Pageable pageable);

    /** 1인 1리뷰 중복 판정 */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * V1.1-1: 상품 평균 별점/개수 재계산 UPDATE (집계).
     * 리뷰 생성/수정/삭제 후 같은 트랜잭션에서 호출한다. 증분이 아닌 재계산으로 최종 일관성 보장.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE product p SET
              rating_count = (SELECT count(*) FROM review r WHERE r.product_id = p.id),
              rating_avg   = COALESCE((SELECT round(avg(r.rating), 1) FROM review r WHERE r.product_id = p.id), 0.0)
            WHERE p.id = :productId
            """, nativeQuery = true)
    void recalculateRating(@Param("productId") Long productId);
}
