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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String CACHE_DETAIL_PREFIX = "product:detail:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 상품 등록 (ADMIN) */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = loadCategory(request.categoryId());
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stock(request.stock())
                .imageUrl(request.imageUrl())
                .category(category)
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    /** 상품 수정 (ADMIN) */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = loadProduct(id);
        Category category = loadCategory(request.categoryId());
        product.update(
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.imageUrl(),
                category
        );
        evictDetailCache(id);
        return ProductResponse.from(product);
    }

    /** 상품 삭제 (ADMIN) */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = loadProduct(id);
        productRepository.delete(product);
        evictDetailCache(id);
    }

    /** 상품 목록 조회 (카테고리 + 키워드 필터) */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findProducts(ProductSearchRequest request) {
        return productRepository.findAllWithFilter(request.categoryId(), request.keyword(), request.pageable())
                .map(ProductSummaryResponse::from);
    }

    /** 상품 상세 조회 — Redis Cache-Aside */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        String cacheKey = CACHE_DETAIL_PREFIX + id;

        // 캐시 조회 — 히트 시 TTL 연장 (자주 조회되는 상품 캐시 유지)
        ProductResponse cached = (ProductResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            redisTemplate.expire(cacheKey, CACHE_TTL);
            log.debug("상품 상세 캐시 히트. id={}", id);
            return cached;
        }

        // DB 조회 후 캐시 저장
        ProductResponse response = ProductResponse.from(loadProduct(id));
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        log.debug("상품 상세 캐시 저장. id={}", id);
        return response;
    }

    // ── private helpers ──────────────────────────────────────────

    private Product loadProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Category loadCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private void evictDetailCache(Long id) {
        redisTemplate.delete(CACHE_DETAIL_PREFIX + id);
        log.debug("상품 상세 캐시 삭제. id={}", id);
    }
}
