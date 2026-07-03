package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductRepositoryCustom {

    /** 카테고리·키워드·가격대·정렬 동적 필터 조회 */
    Page<Product> findAllWithFilter(ProductSearchRequest request, boolean includeBanned);

    /** 상품명 자동완성 후보 — 판매중 상품 한정, distinct name, limit 제한 */
    List<String> findNameSuggestions(String keyword, int limit);
}
