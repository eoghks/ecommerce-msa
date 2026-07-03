package com.ecommerce.product.domain;

import com.querydsl.core.types.OrderSpecifier;

import java.util.Arrays;

/**
 * 상품 목록 정렬 옵션 화이트리스트.
 * 클라이언트는 엔티티 필드명 대신 의미 기반 키(latest/price_asc/price_desc/name)만 전달한다.
 * 미허용·미지정 값은 {@link #LATEST}로 폴백해 임의 필드 정렬을 차단한다.
 */
public enum SortOption {

    LATEST("latest") {
        @Override
        public OrderSpecifier<?> toOrderSpecifier(QProduct product) {
            return product.createdAt.desc();
        }
    },
    PRICE_ASC("price_asc") {
        @Override
        public OrderSpecifier<?> toOrderSpecifier(QProduct product) {
            return product.price.asc();
        }
    },
    PRICE_DESC("price_desc") {
        @Override
        public OrderSpecifier<?> toOrderSpecifier(QProduct product) {
            return product.price.desc();
        }
    },
    NAME("name") {
        @Override
        public OrderSpecifier<?> toOrderSpecifier(QProduct product) {
            return product.name.asc();
        }
    };

    private final String key;

    SortOption(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** QueryDSL 정렬 조건 반환 — 화이트리스트로 매핑된 컬럼/방향만 노출 */
    public abstract OrderSpecifier<?> toOrderSpecifier(QProduct product);

    /** sort 키 → 옵션 매핑. 미허용·null·공백은 {@link #LATEST} 폴백. */
    public static SortOption from(String key) {
        if (key == null || key.isBlank()) {
            return LATEST;
        }
        return Arrays.stream(values())
                .filter(option -> option.key.equalsIgnoreCase(key))
                .findFirst()
                .orElse(LATEST);
    }
}
