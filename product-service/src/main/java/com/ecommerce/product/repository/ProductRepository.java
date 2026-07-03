package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {
    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    /**
     * H-4: 원자적 재고 차감 — 재고가 충분할 때만 차감.
     * 단일 UPDATE의 행 잠금으로 동시 주문 간 read-modify-write 경쟁(oversell)을 차단한다.
     * @return 갱신된 행 수 (0이면 재고 부족 또는 상품 없음)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decreaseStockIfEnough(@Param("id") Long id, @Param("qty") int qty);

    /** 원자적 재고 복구 (취소 보상). @return 갱신된 행 수 (0이면 상품 없음) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int increaseStockAtomic(@Param("id") Long id, @Param("qty") int qty);

    /** 카테고리 참조 상품 수 — 카테고리 삭제 정합성 검사용 */
    long countByCategoryId(Long categoryId);
}
