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

    /** 본인 찜만 삭제(없어도 멱등). 단일 삭제라 영속성 컨텍스트 동기화 불필요. @return 삭제 행 수 */
    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.userId = :userId AND w.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId,
                                   @Param("productId") Long productId);

    /**
     * 내 찜 목록 — Product와 조인해 상품 요약을 한 번에 조회(N+1 방지).
     * 물리 삭제된 상품은 내부 조인으로 자연 배제된다.
     * 정렬은 최신순 고정(서버 계약) — Pageable의 정렬은 무시하고 페이지/사이즈만 사용한다.
     * 생성자 표현식/ORDER BY의 count 자동 파생 위험을 피하려 countQuery를 명시한다.
     */
    @Query(value = """
            SELECT new com.ecommerce.product.repository.WishlistItemProjection(
                p.id, p.name, p.price, p.imageUrl, p.status, w.createdAt)
            FROM Wishlist w
            JOIN Product p ON p.id = w.productId
            WHERE w.userId = :userId
            ORDER BY w.createdAt DESC
            """,
            countQuery = """
            SELECT count(w) FROM Wishlist w
            JOIN Product p ON p.id = w.productId
            WHERE w.userId = :userId
            """)
    Page<WishlistItemProjection> findItemsByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 내 찜 상품 ID 집합(하트 표시용, 경량) */
    @Query("SELECT w.productId FROM Wishlist w WHERE w.userId = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);
}
