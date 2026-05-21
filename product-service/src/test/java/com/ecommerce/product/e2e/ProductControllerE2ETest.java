package com.ecommerce.product.e2e;

import com.ecommerce.product.dto.request.CreateProductRequest;
import com.ecommerce.product.dto.request.UpdateProductRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product API E2E 테스트
 * — 실행 중인 docker-compose 서비스(PostgreSQL:5433, Redis:6379)에 직접 연결
 * — 사전 조건: docker-compose up 상태 필요
 * — 실행 방법: ./gradlew :product-service:e2eTest
 * — CI에서는 build.gradle test { exclude '**/e2e/**' } 로 자동 제외
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/product_db",
        "spring.datasource.username=eoghks",
        "spring.datasource.password=eoghks_local",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@DisplayName("Product API E2E 테스트")
class ProductControllerE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long categoryId;
    private Long productId;

    @BeforeAll
    void setUpCategory() {
        // Redis 잔류 캐시 정리
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // 기존 테스트 데이터 정리 후 카테고리 삽입
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'E2E_%'");
        jdbcTemplate.update("DELETE FROM category WHERE name = 'E2E_전자기기'");
        jdbcTemplate.update(
                "INSERT INTO category (name, created_at, updated_at) VALUES (?, NOW(), NOW())",
                "E2E_전자기기");
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = 'E2E_전자기기'", Long.class);
    }

    @AfterAll
    void cleanUp() {
        if (productId != null) {
            jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
            redisTemplate.delete("product:detail:" + productId);
        }
        jdbcTemplate.update("DELETE FROM category WHERE name = 'E2E_전자기기'");
    }

    // ── 헤더 헬퍼 ──────────────────────────────────────────────────

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "ADMIN");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }

    // ── 1. 상품 등록 ───────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("상품 등록 — ADMIN 권한 정상")
    void createProduct_admin_success() {
        CreateProductRequest request = new CreateProductRequest(
                "E2E_갤럭시 S24", "삼성 스마트폰", 1_200_000L, 50, null, categoryId);

        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("E2E_갤럭시 S24");
        assertThat(response.getBody().price()).isEqualTo(1_200_000L);
        assertThat(response.getBody().stock()).isEqualTo(50);

        productId = response.getBody().id();
    }

    @Test
    @Order(2)
    @DisplayName("상품 등록 — ADMIN 권한 없음 403")
    void createProduct_noAdmin_forbidden() {
        CreateProductRequest request = new CreateProductRequest(
                "E2E_아이폰 15", "애플 스마트폰", 1_500_000L, 30, null, categoryId);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(request, userHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 2. 상품 상세 조회 + 캐시 ──────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("상품 상세 조회 — 캐시 미스 → DB 조회 후 캐시 저장")
    void getProduct_cacheMiss() {
        assertThat(productId).isNotNull();
        redisTemplate.delete("product:detail:" + productId);

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity(
                "/api/v1/products/" + productId, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("E2E_갤럭시 S24");

        // 캐시 저장 확인
        String cached = redisTemplate.opsForValue().get("product:detail:" + productId);
        assertThat(cached).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("상품 상세 조회 — 캐시 히트 + TTL 리셋")
    void getProduct_cacheHit() {
        assertThat(productId).isNotNull();

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity(
                "/api/v1/products/" + productId, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("E2E_갤럭시 S24");

        // 캐시 여전히 존재 확인
        String cached = redisTemplate.opsForValue().get("product:detail:" + productId);
        assertThat(cached).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("상품 상세 조회 — 존재하지 않는 상품 404")
    void getProduct_notFound() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/v1/products/999999", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 3. 목록 조회 ──────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("상품 목록 조회 — 필터 없음")
    void findProducts_noFilter() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E_갤럭시 S24");
    }

    @Test
    @Order(7)
    @DisplayName("상품 목록 조회 — 키워드 필터")
    void findProducts_keywordFilter() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products?keyword=E2E_갤럭시", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E_갤럭시 S24");
    }

    @Test
    @Order(8)
    @DisplayName("상품 목록 조회 — 카테고리 필터")
    void findProducts_categoryFilter() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products?categoryId=" + categoryId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E_갤럭시 S24");
    }

    // ── 4. 상품 수정 ──────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("상품 수정 — ADMIN 권한 정상 + 캐시 무효화")
    void updateProduct_admin_success() {
        assertThat(productId).isNotNull();

        UpdateProductRequest request = new UpdateProductRequest(
                "E2E_갤럭시 S24 Ultra", "업그레이드 버전", 1_500_000L, 30, null, categoryId);

        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()),
                ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("E2E_갤럭시 S24 Ultra");
        assertThat(response.getBody().price()).isEqualTo(1_500_000L);

        // 수정 후 캐시 무효화 확인
        String cached = redisTemplate.opsForValue().get("product:detail:" + productId);
        assertThat(cached).isNull();
    }

    // ── 5. 상품 삭제 ──────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("상품 삭제 — ADMIN 권한 정상 + 이후 404")
    void deleteProduct_admin_success() {
        assertThat(productId).isNotNull();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 삭제 후 조회 시 404
        ResponseEntity<Void> getResponse = restTemplate.getForEntity(
                "/api/v1/products/" + productId, Void.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        productId = null; // AfterAll 중복 삭제 방지
    }
}
