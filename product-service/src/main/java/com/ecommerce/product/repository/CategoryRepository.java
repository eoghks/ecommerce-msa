package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 카테고리명 중복 검사 */
    boolean existsByName(String name);
}
