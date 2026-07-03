package com.ecommerce.product.repository;

import com.ecommerce.product.config.JpaConfig;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.domain.ProductStatus;
import com.ecommerce.product.domain.Wishlist;
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

/**
 * WishlistRepository 통합 테스트 — 실제 PostgreSQL(Testcontainers).
 * 목킹 단위테스트가 못 잡는 countQuery/정렬/상품조인(판매금지·물리삭제) 경로를 검증한다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("WishlistRepository 통합 테스트")
class WishlistRepositoryTest {

    private static final Long USER_ID = 100L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Product active1;
    private Product active2;
    private Product banned;

    @BeforeEach
    void setUp() {
        wishlistRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = categoryRepository.save(Category.builder().name("전자기기").build());

        active1 = saveProduct("갤럭시 S24", category);
        active2 = saveProduct("아이폰 15", category);
        banned  = saveProduct("판매금지폰", category);
        banned.ban();
        productRepository.saveAndFlush(banned);
    }

    private Product saveProduct(String name, Category category) {
        return productRepository.saveAndFlush(Product.builder()
                .name(name)
                .description("설명")
                .price(1_000_000L)
                .stock(10)
                .category(category)
                .build());
    }

    private void addWish(Long productId) throws InterruptedException {
        wishlistRepository.saveAndFlush(Wishlist.builder()
                .userId(USER_ID)
                .productId(productId)
                .build());
        // @CreatedDate 시간차 확보(정렬 검증 안정화)
        Thread.sleep(5);
    }

    @Test
    @DisplayName("정렬 계약 — 최신 찜이 먼저(createdAt DESC 고정)")
    void findItemsByUserId_ordering() throws InterruptedException {
        addWish(active1.getId());
        addWish(active2.getId());

        Page<WishlistItemProjection> result =
                wishlistRepository.findItemsByUserId(USER_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(WishlistItemProjection::productId)
                .containsExactly(active2.getId(), active1.getId());
    }

    @Test
    @DisplayName("페이징 — total count는 명시 countQuery로 정확히 집계")
    void findItemsByUserId_totalCount() throws InterruptedException {
        addWish(active1.getId());
        addWish(active2.getId());
        addWish(banned.getId());

        Page<WishlistItemProjection> firstPage =
                wishlistRepository.findItemsByUserId(USER_ID, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품 조인 — 판매금지 상품도 status=BANNED 라벨로 함께 노출")
    void findItemsByUserId_bannedLabel() throws InterruptedException {
        addWish(banned.getId());

        Page<WishlistItemProjection> result =
                wishlistRepository.findItemsByUserId(USER_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo(banned.getId());
                    assertThat(item.status()).isEqualTo(ProductStatus.BANNED);
                });
    }

    @Test
    @DisplayName("상품 조인 — 물리 삭제된 상품은 목록·count에서 제외")
    void findItemsByUserId_physicallyDeletedExcluded() throws InterruptedException {
        addWish(active1.getId());
        addWish(active2.getId());

        // active2를 물리 삭제 → 조인에서 자연 배제되어야 함
        productRepository.delete(active2);
        productRepository.flush();

        Page<WishlistItemProjection> result =
                wishlistRepository.findItemsByUserId(USER_ID, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(WishlistItemProjection::productId)
                .containsExactly(active1.getId());
    }

    @Test
    @DisplayName("유니크 제약 — 동일 (userId, productId) 중복 저장 차단")
    void uniqueConstraint_blocksDuplicate() {
        wishlistRepository.saveAndFlush(Wishlist.builder()
                .userId(USER_ID).productId(active1.getId()).build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                wishlistRepository.saveAndFlush(Wishlist.builder()
                        .userId(USER_ID).productId(active1.getId()).build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("본인 찜만 삭제 — 삭제 행 수 반환")
    void deleteByUserIdAndProductId() {
        wishlistRepository.saveAndFlush(Wishlist.builder()
                .userId(USER_ID).productId(active1.getId()).build());

        int deleted = wishlistRepository.deleteByUserIdAndProductId(USER_ID, active1.getId());

        assertThat(deleted).isEqualTo(1);
        List<Long> remaining = wishlistRepository.findProductIdsByUserId(USER_ID);
        assertThat(remaining).isEmpty();
    }
}
