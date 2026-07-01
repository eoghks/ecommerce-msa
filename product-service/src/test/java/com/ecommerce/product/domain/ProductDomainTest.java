package com.ecommerce.product.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 도메인 단위 테스트")
class ProductDomainTest {

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().name("전자기기").build();
    }

    private Product createProduct(int stock) {
        return Product.builder()
                .name("테스트 상품")
                .description("상품 설명")
                .price(10_000L)
                .stock(stock)
                .imageUrl("image.jpg")
                .category(category)
                .build();
    }

    @Test
    @DisplayName("재고 차감 — 정상")
    void decreaseStock_success() {
        Product product = createProduct(10);
        product.decreaseStock(3);
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 차감 — 전량 차감")
    void decreaseStock_allStock() {
        Product product = createProduct(5);
        product.decreaseStock(5);
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 차감 — 재고 부족 시 예외")
    void decreaseStock_insufficient() {
        Product product = createProduct(2);
        assertThatThrownBy(() -> product.decreaseStock(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("재고 복구 — 정상")
    void increaseStock_success() {
        Product product = createProduct(5);
        product.increaseStock(3);
        assertThat(product.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("상품 정보 수정 — 전체 필드 반영")
    void update_success() {
        Product product = createProduct(10);
        Category newCategory = Category.builder().name("가전").build();

        product.update("수정된 상품", "새 설명", 20_000L, 5, "new.jpg", newCategory);

        assertThat(product.getName()).isEqualTo("수정된 상품");
        assertThat(product.getDescription()).isEqualTo("새 설명");
        assertThat(product.getPrice()).isEqualTo(20_000L);
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getImageUrl()).isEqualTo("new.jpg");
        assertThat(product.getCategory().getName()).isEqualTo("가전");
    }
}
