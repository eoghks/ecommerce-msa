package com.ecommerce.product.e2e;

import com.ecommerce.product.dto.request.CategoryCreateRequest;
import com.ecommerce.product.dto.request.CategoryUpdateRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category 관리 API E2E 테스트 (B-05)
 * — Testcontainers(PostgreSQL + Redis) static 초기화로 CI/로컬 모두 동작
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.kafka.bootstrap-servers=localhost:19999",
        "spring.kafka.listener.auto-startup=false"
})
@DisplayName("Category 관리 API E2E 테스트")
class CategoryControllerE2ETest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long categoryId;
    private Long inUseCategoryId;
    private Long inUseProductId;

    @AfterAll
    void cleanUp() {
        if (inUseProductId != null) {
            jdbcTemplate.update("DELETE FROM product WHERE id = ?", inUseProductId);
        }
        jdbcTemplate.update("DELETE FROM category WHERE name LIKE 'E2E_CAT_%'");
        if (categoryId != null) {
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
        }
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

    // ── 1. 생성 ───────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("카테고리 생성 — ADMIN 정상")
    void createCategory_admin_success() {
        CategoryCreateRequest request = new CategoryCreateRequest("E2E_CAT_전자기기");

        ResponseEntity<CategoryResponse> response = restTemplate.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("E2E_CAT_전자기기");

        categoryId = response.getBody().id();
    }

    @Test
    @Order(2)
    @DisplayName("카테고리 생성 — 비ADMIN 403")
    void createCategory_noAdmin_forbidden() {
        CategoryCreateRequest request = new CategoryCreateRequest("E2E_CAT_불가");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(request, userHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    @DisplayName("카테고리 생성 — 중복 이름 409")
    void createCategory_duplicate_conflict() {
        CategoryCreateRequest request = new CategoryCreateRequest("E2E_CAT_전자기기");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── 2. 조회 ───────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("카테고리 목록 조회 — 공개")
    void getCategories_public() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/categories", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("E2E_CAT_전자기기");
    }

    // ── 3. 수정 ───────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("카테고리 수정 — ADMIN 정상")
    void updateCategory_admin_success() {
        assertThat(categoryId).isNotNull();
        CategoryUpdateRequest request = new CategoryUpdateRequest("E2E_CAT_가전");

        ResponseEntity<CategoryResponse> response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId,
                HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()),
                CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("E2E_CAT_가전");
    }

    @Test
    @Order(6)
    @DisplayName("카테고리 수정 — 비ADMIN 403")
    void updateCategory_noAdmin_forbidden() {
        assertThat(categoryId).isNotNull();
        CategoryUpdateRequest request = new CategoryUpdateRequest("E2E_CAT_해킹");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId,
                HttpMethod.PUT,
                new HttpEntity<>(request, userHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 4. 삭제 정합성 ────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("카테고리 삭제 — 참조 상품 존재 시 409")
    void deleteCategory_inUse_conflict() {
        jdbcTemplate.update(
                "INSERT INTO category (name, created_at, updated_at) VALUES (?, NOW(), NOW())",
                "E2E_CAT_사용중");
        inUseCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = 'E2E_CAT_사용중'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO product (name, price, stock, category_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                "E2E_CAT_참조상품", 1000L, 5, inUseCategoryId, "ACTIVE");
        inUseProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = 'E2E_CAT_참조상품'", Long.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories/" + inUseCategoryId,
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(8)
    @DisplayName("카테고리 삭제 — 비ADMIN 403")
    void deleteCategory_noAdmin_forbidden() {
        assertThat(categoryId).isNotNull();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId,
                HttpMethod.DELETE,
                new HttpEntity<>(userHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(9)
    @DisplayName("카테고리 삭제 — ADMIN 정상 (참조 없음)")
    void deleteCategory_admin_success() {
        assertThat(categoryId).isNotNull();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/categories/" + categoryId,
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        categoryId = null; // AfterAll 중복 삭제 방지
    }
}
