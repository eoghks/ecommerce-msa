package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.ProductStatus;
import com.ecommerce.product.domain.QCategory;
import com.ecommerce.product.domain.QProduct;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QProduct product = QProduct.product;
    private static final QCategory category = QCategory.category;

    @Override
    public Page<Product> findAllWithFilter(ProductSearchRequest request, boolean includeBanned) {
        BooleanBuilder condition = buildCondition(request, includeBanned);
        Pageable pageable = request.pageable();

        // 목록 조회 (N+1 방지 — category fetch join). 정렬은 화이트리스트 SortOption 매핑.
        List<Product> content = queryFactory
                .selectFrom(product)
                .join(product.category, category).fetchJoin()
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(request.sort().toOrderSpecifier(product))
                .fetch();

        // 카운트 쿼리 (fetch join 제외 — 카운트엔 불필요)
        long total = queryFactory
                .select(product.count())
                .from(product)
                .join(product.category, category)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<String> findNameSuggestions(String keyword, int limit) {
        // 판매중 상품 한정, 상품명 prefix 일치, 중복 제거 후 limit 제한 (파라미터 바인딩)
        return queryFactory
                .select(product.name).distinct()
                .from(product)
                .where(product.status.eq(ProductStatus.ACTIVE)
                        .and(product.name.startsWith(keyword)))
                .orderBy(product.name.asc())
                .limit(limit)
                .fetch();
    }

    /** 동적 조건 조립 — null이면 해당 조건 제외 */
    private BooleanBuilder buildCondition(ProductSearchRequest request, boolean includeBanned) {
        BooleanBuilder builder = new BooleanBuilder();
        if (request.categoryId() != null) {
            builder.and(category.id.eq(request.categoryId()));
        }
        if (request.keyword() != null && !request.keyword().isBlank()) {
            builder.and(product.name.contains(request.keyword()));
        }
        if (request.minPrice() != null) {
            builder.and(product.price.goe(request.minPrice()));
        }
        if (request.maxPrice() != null) {
            builder.and(product.price.loe(request.maxPrice()));
        }
        // 공개 목록(includeBanned=false)은 판매 금지 상품 제외
        if (!includeBanned) {
            builder.and(product.status.eq(ProductStatus.ACTIVE));
        }
        return builder;
    }
}
