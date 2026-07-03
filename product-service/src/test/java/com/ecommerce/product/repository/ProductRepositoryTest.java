package com.ecommerce.product.repository;

import com.ecommerce.product.config.JpaConfig;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.SortOption;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("ProductRepository 통합 테스트")
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    /** 검색 요청 조립 헬퍼 — 정렬 기본 latest */
    private ProductSearchRequest request(Long categoryId, String keyword,
                                         Long minPrice, Long maxPrice, SortOption sort) {
        return new ProductSearchRequest(categoryId, keyword, minPrice, maxPrice, sort,
                PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("필터 없음 — 전체 상품 조회")
    void findAllWithFilter_noFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, null, SortOption.LATEST), false);

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("카테고리 필터 — 전자기기만 조회")
    void findAllWithFilter_categoryFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(electronics.getId(), null, null, null, SortOption.LATEST), false);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(p -> p.getCategory().getId().equals(electronics.getId()));
    }

    @Test
    @DisplayName("키워드 필터 — '갤럭시' 포함 조회")
    void findAllWithFilter_keywordFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, "갤럭시", null, null, SortOption.LATEST), false);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("갤럭시 S24");
    }

    @Test
    @DisplayName("카테고리 + 키워드 복합 필터")
    void findAllWithFilter_combinedFilter() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(electronics.getId(), "아이폰", null, null, SortOption.LATEST), false);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("아이폰 15");
    }

    @Test
    @DisplayName("일치하는 상품 없음 — 빈 페이지 반환")
    void findAllWithFilter_noResult() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, "존재하지않는키워드xyz", null, null, SortOption.LATEST), false);

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("페이징 — size=2, page=0")
    void findAllWithFilter_paging() {
        Page<Product> result = productRepository.findAllWithFilter(
                new ProductSearchRequest(null, null, null, null, SortOption.LATEST,
                        PageRequest.of(0, 2)), false);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    // ── 정렬 화이트리스트 ──────────────────────────────────────────

    @Test
    @DisplayName("정렬 latest — createdAt 내림차순")
    void sort_latest() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, null, SortOption.LATEST), false);

        // 마지막 저장(나이키)이 가장 최신 → 첫 번째
        assertThat(result.getContent().get(0).getName()).isEqualTo("나이키 운동화");
    }

    @Test
    @DisplayName("정렬 price_asc — 가격 오름차순")
    void sort_priceAsc() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, null, SortOption.PRICE_ASC), false);

        assertThat(result.getContent()).extracting(Product::getPrice)
                .containsExactly(150_000L, 1_200_000L, 1_500_000L);
    }

    @Test
    @DisplayName("정렬 price_desc — 가격 내림차순")
    void sort_priceDesc() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, null, SortOption.PRICE_DESC), false);

        assertThat(result.getContent()).extracting(Product::getPrice)
                .containsExactly(1_500_000L, 1_200_000L, 150_000L);
    }

    @Test
    @DisplayName("정렬 name — 상품명 오름차순")
    void sort_name() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, null, SortOption.NAME), false);

        assertThat(result.getContent()).extracting(Product::getName)
                .containsExactly("갤럭시 S24", "나이키 운동화", "아이폰 15");
    }

    // ── 가격대 필터 ────────────────────────────────────────────────

    @Test
    @DisplayName("가격 필터 — min만 (>= 1,000,000)")
    void priceFilter_minOnly() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, 1_000_000L, null, SortOption.PRICE_ASC), false);

        assertThat(result.getContent()).extracting(Product::getPrice)
                .containsExactly(1_200_000L, 1_500_000L);
    }

    @Test
    @DisplayName("가격 필터 — max만 (<= 200,000)")
    void priceFilter_maxOnly() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, null, 200_000L, SortOption.PRICE_ASC), false);

        assertThat(result.getContent()).extracting(Product::getPrice)
                .containsExactly(150_000L);
    }

    @Test
    @DisplayName("가격 필터 — min·max 둘 다 (100,000 ~ 1,300,000)")
    void priceFilter_minAndMax() {
        Page<Product> result = productRepository.findAllWithFilter(
                request(null, null, 100_000L, 1_300_000L, SortOption.PRICE_ASC), false);

        assertThat(result.getContent()).extracting(Product::getPrice)
                .containsExactly(150_000L, 1_200_000L);
    }

    // ── 자동완성 ──────────────────────────────────────────────────

    @Test
    @DisplayName("자동완성 — prefix 매칭 (판매중 한정)")
    void suggestions_prefix() {
        List<String> names = productRepository.findNameSuggestions("갤럭시", 10);

        assertThat(names).containsExactly("갤럭시 S24");
    }

    @Test
    @DisplayName("자동완성 — limit 제한")
    void suggestions_limit() {
        // '아'로 시작하는 상품 추가
        productRepository.save(Product.builder()
                .name("아디다스 티셔츠").price(50_000L).stock(10).category(fashion).build());

        List<String> names = productRepository.findNameSuggestions("아", 1);

        assertThat(names).hasSize(1);
    }

    @Test
    @DisplayName("자동완성 — 판매금지 상품 제외")
    void suggestions_excludesBanned() {
        Product banned = productRepository.save(Product.builder()
                .name("갤럭시 워치").price(300_000L).stock(5).category(electronics).build());
        banned.ban();
        productRepository.saveAndFlush(banned);

        List<String> names = productRepository.findNameSuggestions("갤럭시", 10);

        assertThat(names).containsExactly("갤럭시 S24");
    }
}
