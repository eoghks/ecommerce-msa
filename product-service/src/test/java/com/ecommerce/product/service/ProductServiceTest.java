package com.ecommerce.product.service;

import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.request.CreateProductRequest;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import com.ecommerce.product.dto.request.UpdateProductRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.exception.CategoryNotFoundException;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper redisObjectMapper;

    @InjectMocks private ProductService productService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = Category.builder().name("전자기기").build();
        ReflectionTestUtils.setField(category, "id", 1L);

        product = Product.builder()
                .name("테스트 상품")
                .description("상품 설명")
                .price(10_000L)
                .stock(10)
                .imageUrl("image.jpg")
                .category(category)
                .build();
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    // ── createProduct ─────────────────────────────────────────────

    @Test
    @DisplayName("상품 등록 — 정상")
    void createProduct_success() {
        CreateProductRequest request = new CreateProductRequest(
                "테스트 상품", "상품 설명", 10_000L, 10, "image.jpg", 1L);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willReturn(product);

        ProductResponse response = productService.createProduct(request);

        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.price()).isEqualTo(10_000L);
        assertThat(response.stock()).isEqualTo(10);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 등록 — 존재하지 않는 카테고리 예외")
    void createProduct_categoryNotFound() {
        CreateProductRequest request = new CreateProductRequest(
                "테스트 상품", "설명", 10_000L, 10, null, 99L);

        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    // ── updateProduct ─────────────────────────────────────────────

    @Test
    @DisplayName("상품 수정 — 정상")
    void updateProduct_success() {
        UpdateProductRequest request = new UpdateProductRequest(
                "수정 상품", "새 설명", 20_000L, 5, "new.jpg", 1L);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(redisTemplate.delete(anyString())).willReturn(true);

        ProductResponse response = productService.updateProduct(1L, request);

        assertThat(response.name()).isEqualTo("수정 상품");
        assertThat(response.price()).isEqualTo(20_000L);
        verify(redisTemplate).delete("product:detail:1");
    }

    @Test
    @DisplayName("상품 수정 — 존재하지 않는 상품 예외")
    void updateProduct_productNotFound() {
        UpdateProductRequest request = new UpdateProductRequest(
                "수정 상품", "설명", 20_000L, 5, null, 1L);

        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ── deleteProduct ─────────────────────────────────────────────

    @Test
    @DisplayName("상품 삭제 — 정상")
    void deleteProduct_success() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(redisTemplate.delete(anyString())).willReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).delete(product);
        verify(redisTemplate).delete("product:detail:1");
    }

    @Test
    @DisplayName("상품 삭제 — 존재하지 않는 상품 예외")
    void deleteProduct_productNotFound() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).delete(any());
    }

    // ── getProduct ────────────────────────────────────────────────

    @Test
    @DisplayName("상품 상세 조회 — 캐시 히트")
    void getProduct_cacheHit() throws JsonProcessingException {
        ProductResponse cached = ProductResponse.from(product);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:detail:1")).willReturn("{\"id\":1}");
        given(redisObjectMapper.readValue("{\"id\":1}", ProductResponse.class)).willReturn(cached);

        ProductResponse response = productService.getProduct(1L);

        assertThat(response.id()).isEqualTo(1L);
        // 캐시 히트 시 TTL 리셋
        verify(redisTemplate).expire(eq("product:detail:1"), any(Duration.class));
        // DB 조회 없음
        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("상품 상세 조회 — 캐시 미스 → DB 조회 후 캐시 저장")
    void getProduct_cacheMiss() throws JsonProcessingException {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:detail:1")).willReturn(null);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(redisObjectMapper.writeValueAsString(any(ProductResponse.class))).willReturn("{\"id\":1}");

        ProductResponse response = productService.getProduct(1L);

        assertThat(response.name()).isEqualTo("테스트 상품");
        verify(productRepository).findById(1L);
        verify(valueOperations).set(eq("product:detail:1"), eq("{\"id\":1}"), any(Duration.class));
    }

    @Test
    @DisplayName("상품 상세 조회 — 존재하지 않는 상품 예외")
    void getProduct_notFound() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:detail:99")).willReturn(null);
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ── findProducts ──────────────────────────────────────────────

    @Test
    @DisplayName("상품 목록 조회 — 필터 없음")
    void findProducts_noFilter() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Product> productPage = new PageImpl<>(List.of(product), pageable, 1);

        given(productRepository.findAllWithFilter(null, null, pageable))
                .willReturn(productPage);

        Page<ProductSummaryResponse> result = productService.findProducts(
                new ProductSearchRequest(null, null, pageable));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("테스트 상품");
    }

    @Test
    @DisplayName("상품 목록 조회 — 카테고리 + 키워드 필터")
    void findProducts_withFilter() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Product> productPage = new PageImpl<>(List.of(product), pageable, 1);

        given(productRepository.findAllWithFilter(1L, "테스트", pageable))
                .willReturn(productPage);

        Page<ProductSummaryResponse> result = productService.findProducts(
                new ProductSearchRequest(1L, "테스트", pageable));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).categoryId()).isEqualTo(1L);
    }
}
