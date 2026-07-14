package com.ecommerce.product.repository;

import com.ecommerce.product.config.JpaConfig;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.Review;
import jakarta.persistence.EntityManager;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("ReviewRepository 통합 테스트")
class ReviewRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;

    private Product product;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = categoryRepository.save(Category.builder().name("전자기기").build());
        product = productRepository.save(Product.builder()
                .name("갤럭시 S24")
                .description("삼성 스마트폰")
                .price(1_200_000L)
                .stock(50)
                .category(category)
                .build());
    }

    @Test
    @DisplayName("상품별 리뷰 목록 — 최신순 페이징")
    void findByProductId_paging() {
        for (int i = 1; i <= 15; i++) {
            reviewRepository.save(buildReview(i % 5 + 1));
        }

        Page<Review> page = reviewRepository.findByProductId(
                product.getId(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getContent()).hasSize(10);
    }

    @Test
    @DisplayName("평균 재계산 — 생성/삭제 반영")
    void recalculateRating_createAndDelete() {
        Review r1 = reviewRepository.save(buildReview(5));
        reviewRepository.save(buildReview(3));

        reviewRepository.recalculateRating(product.getId());
        Product refreshed = reloadProduct();
        // (5 + 3) / 2 = 4.0, count 2
        assertThat(refreshed.getRatingCount()).isEqualTo(2);
        assertThat(refreshed.getRatingAvg()).isEqualByComparingTo(new BigDecimal("4.0"));

        reviewRepository.delete(r1);
        reviewRepository.recalculateRating(product.getId());
        Product afterDelete = reloadProduct();
        // 남은 리뷰 rating 3 → avg 3.0, count 1
        assertThat(afterDelete.getRatingCount()).isEqualTo(1);
        assertThat(afterDelete.getRatingAvg()).isEqualByComparingTo(new BigDecimal("3.0"));
    }

    @Test
    @DisplayName("평균 재계산 — 리뷰 0건이면 0.0/0")
    void recalculateRating_empty() {
        reviewRepository.recalculateRating(product.getId());
        Product refreshed = reloadProduct();

        assertThat(refreshed.getRatingCount()).isZero();
        assertThat(refreshed.getRatingAvg()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("1인 1리뷰 중복 판정")
    void existsByUserIdAndProductId() {
        reviewRepository.save(buildReview(5));

        assertThat(reviewRepository.existsByUserIdAndProductId(1L, product.getId())).isTrue();
        assertThat(reviewRepository.existsByUserIdAndProductId(2L, product.getId())).isFalse();
    }

    private Review buildReview(int rating) {
        return Review.builder()
                .productId(product.getId())
                .userId(1L)
                .rating(rating)
                .content("리뷰 내용")
                .build();
    }

    /** 네이티브 UPDATE 후 1차 캐시 우회 — DB 값 재조회 */
    private Product reloadProduct() {
        entityManager.flush();
        entityManager.clear();
        return productRepository.findById(product.getId()).orElseThrow();
    }
}
