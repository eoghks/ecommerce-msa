package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    Page<Product> findAllWithFilter(Long categoryId, String keyword, Pageable pageable);
}
