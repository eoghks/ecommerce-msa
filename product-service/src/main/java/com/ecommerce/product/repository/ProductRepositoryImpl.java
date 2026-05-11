package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.QCategory;
import com.ecommerce.product.domain.QProduct;
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
    public Page<Product> findAllWithFilter(Long categoryId, String keyword, Pageable pageable) {
        BooleanBuilder condition = buildCondition(categoryId, keyword);

        // 목록 조회 (N+1 방지 — category fetch join)
        List<Product> content = queryFactory
                .selectFrom(product)
                .join(product.category, category).fetchJoin()
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(product.createdAt.desc())
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

    /** 동적 조건 조립 — null이면 해당 조건 제외 */
    private BooleanBuilder buildCondition(Long categoryId, String keyword) {
        BooleanBuilder builder = new BooleanBuilder();
        if (categoryId != null) {
            builder.and(category.id.eq(categoryId));
        }
        if (keyword != null && !keyword.isBlank()) {
            builder.and(product.name.contains(keyword));
        }
        return builder;
    }
}
