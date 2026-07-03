package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /** 중복 찜 확인(멱등 처리용) */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /** 본인 찜만 삭제(없어도 멱등). @return 삭제 행 수 */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Wishlist w WHERE w.userId = :userId AND w.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId,
                                   @Param("productId") Long productId);

    /**
     * 내 찜 목록 — Product와 조인해 상품 요약을 한 번에 조회(N+1 방지).
     * 물리 삭제된 상품은 내부 조인으로 자연 배제된다.
     */
    @Query("""
            SELECT new com.ecommerce.product.repository.WishlistItemProjection(
                p.id, p.name, p.price, p.imageUrl, p.status, w.createdAt)
            FROM Wishlist w
            JOIN Product p ON p.id = w.productId
            WHERE w.userId = :userId
            ORDER BY w.createdAt DESC
            """)
    Page<WishlistItemProjection> findItemsByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 내 찜 상품 ID 집합(하트 표시용, 경량) */
    @Query("SELECT w.productId FROM Wishlist w WHERE w.userId = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);
}
