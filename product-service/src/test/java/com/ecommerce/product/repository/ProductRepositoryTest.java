package com.ecommerce.product.repository;

import com.ecommerce.product.config.JpaConfig;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@DisplayName("ProductRepository 통합 테스트")
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Category electronics;
    private Category fashion;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        electronics = categoryRepository.save(Category.builder().name("전자기기").build());
        fashion     = categoryRepository.save(Category.builder().name("패션").build());

        productRepository.save(Product.builder()
                .name("갤럭시 S24")
                .description("삼성 스마트폰")
                .price(1_200_000L)
                .stock(50)
                .category(electronics)
                .build());

        productRepository.save(Product.builder()
                .name("아이폰 15")
                .description("애플 스마트폰")
                .price(1_500_000L)
                .stock(30)
                .category(electronics)
                .build());

        productRepository.save(Product.builder()
                .name("나이키 운동화")
                .description("스포츠 신발")
                .price(150_000L)
                .stock(100)
                .category(fashion)
                .build());
    }

    @Test
    @DisplayName("필터 없음 — 전체 상품 조회")
    void findAllWithFilter_noFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("카테고리 필터 — 전자기기만 조회")
    void findAllWithFilter_categoryFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                electronics.getId(), null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(p -> p.getCategory().getId().equals(electronics.getId()));
    }

    @Test
    @DisplayName("키워드 필터 — '갤럭시' 포함 조회")
    void findAllWithFilter_keywordFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                null, "갤럭시", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("갤럭시 S24");
    }

    @Test
    @DisplayName("카테고리 + 키워드 복합 필터")
    void findAllWithFilter_combinedFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                electronics.getId(), "아이폰", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("아이폰 15");
    }

    @Test
    @DisplayName("일치하는 상품 없음 — 빈 페이지 반환")
    void findAllWithFilter_noResult() {
        Page<Product> result = productRepository.findAllWithFilter(
                null, "존재하지않는키워드xyz", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("페이징 — size=2, page=0")
    void findAllWithFilter_paging() {
        Page<Product> result = productRepository.findAllWithFilter(
                null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }
}
