package com.ecommerce.product.service;

import com.ecommerce.product.client.UserClient;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.request.CreateProductRequest;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import com.ecommerce.product.dto.request.UpdateProductRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.exception.CategoryNotFoundException;
import com.ecommerce.product.exception.MissingSellerIdException;
import com.ecommerce.product.exception.NotProductOwnerException;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String CACHE_DETAIL_PREFIX = "product:detail:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final UserClient userClient;

    /** 상품 등록 (ADMIN/SELLER) */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, Long sellerId, String role) {
        requireSellerId(sellerId, role);
        Category category = loadCategory(request.categoryId());
        // ADMIN은 sellerId null (플랫폼 상품), SELLER는 본인 ID
        Long resolvedSellerId = "ADMIN".equals(role) ? null : sellerId;
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stock(request.stock())
                .imageUrl(request.imageUrl())
                .sellerId(resolvedSellerId)
                .category(category)
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    /** 상품 수정 (ADMIN: 전체, SELLER: 본인 것만) */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request, Long userId, String role) {
        requireSellerId(userId, role);
        Product product = loadProduct(id);
        if ("SELLER".equals(role) && !product.isOwnedBy(userId)) {
            throw new NotProductOwnerException(id);
        }
        Category category = loadCategory(request.categoryId());
        product.update(request.name(), request.description(), request.price(),
                request.stock(), request.imageUrl(), category);
        evictDetailCache(id);
        return ProductResponse.from(product);
    }

    /** 상품 삭제 (ADMIN: 전체, SELLER: 본인 것만) */
    @Transactional
    public void deleteProduct(Long id, Long userId, String role) {
        requireSellerId(userId, role);
        Product product = loadProduct(id);
        if ("SELLER".equals(role) && !product.isOwnedBy(userId)) {
            throw new NotProductOwnerException(id);
        }
        productRepository.delete(product);
        evictDetailCache(id);
    }

    /** 판매 금지 (ADMIN) */
    @Transactional
    public void banProduct(Long id) {
        loadProduct(id).ban();
        evictDetailCache(id);  // 캐시 제거 → 공개 조회 즉시 차단
    }

    /** 판매 금지 해제 (ADMIN) */
    @Transactional
    public void unbanProduct(Long id) {
        loadProduct(id).unban();
        evictDetailCache(id);
    }

    /** 내 상품 목록 (SELLER 전용) */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getMyProducts(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable)
                .map(ProductSummaryResponse::from);
    }

    /** 상품 목록 조회 (카테고리 + 키워드 필터) */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findProducts(ProductSearchRequest request) {
        return findProducts(request, false);
    }

    /**
     * 상품 목록 조회.
     * enrichSeller=true (ADMIN) 이면 판매자명/이메일을 Auth Service에서 배치 조회해 포함.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findProducts(ProductSearchRequest request, boolean enrichSeller) {
        // ADMIN(enrichSeller=true)만 판매 금지 상품 포함, 공개 목록은 제외
        Page<Product> page = productRepository.findAllWithFilter(request, enrichSeller);

        if (!enrichSeller) {
            return page.map(ProductSummaryResponse::from);
        }

        // 판매자 ID 모아 한 번에 조회 (N+1 방지) — sellerId가 null인 ADMIN 상품은 제외
        List<Long> sellerIds = page.getContent().stream()
                .map(Product::getSellerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserClient.UserSummary> sellers = userClient.getUsersByIds(sellerIds);

        return page.map(p -> ProductSummaryResponse.from(p, sellers.get(p.getSellerId())));
    }

    /**
     * 상품명 자동완성 후보 조회.
     * keyword가 공백/빈 값이면 빈 목록. 판매금지/삭제 상품은 제외한다.
     */
    @Transactional(readOnly = true)
    public List<String> suggestNames(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return productRepository.findNameSuggestions(keyword.trim(), limit);
    }

    /** 상품 상세 조회 — Redis Cache-Aside */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        String cacheKey = CACHE_DETAIL_PREFIX + id;

        // 캐시 조회 — 히트 시 TTL 연장 (자주 조회되는 상품 캐시 유지)
        // M-5: 캐시 손상 시 500 대신 캐시 미스로 폴백 (손상 키 삭제 후 DB 재조회)
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            ProductResponse cached = tryDeserialize(cachedJson);
            if (cached != null) {
                redisTemplate.expire(cacheKey, CACHE_TTL);
                log.debug("상품 상세 캐시 히트. id={}", id);
                return cached;
            }
            // 역직렬화 실패 — 손상 캐시 제거하고 DB 재조회로 진행
            log.warn("상품 캐시 손상 — 삭제 후 DB 재조회. id={}", id);
            redisTemplate.delete(cacheKey);
        }

        // DB 조회 — 판매 금지 상품은 공개 조회에서 404 (캐시에 담지 않음)
        Product product = loadProduct(id);
        if (product.isBanned()) {
            throw new ProductNotFoundException(id);
        }
        ProductResponse response = ProductResponse.from(product);
        redisTemplate.opsForValue().set(cacheKey, serialize(response), CACHE_TTL);
        log.debug("상품 상세 캐시 저장. id={}", id);
        return response;
    }

    private String serialize(ProductResponse response) {
        try {
            return redisObjectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            // DTO 직렬화 실패 — 프로그래밍 오류
            throw new IllegalStateException("상품 캐시 직렬화 실패. id=" + response.id(), e);
        }
    }

    /** 캐시 역직렬화 — 실패 시 null 반환(캐시 미스 폴백). */
    private ProductResponse tryDeserialize(String json) {
        try {
            return redisObjectMapper.readValue(json, ProductResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("상품 캐시 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── private helpers ──────────────────────────────────────────

    /** M-N2: SELLER 경로는 userId(X-User-Id) 필수. ADMIN은 null 허용(플랫폼 상품). */
    private void requireSellerId(Long userId, String role) {
        if (!"ADMIN".equals(role) && userId == null) {
            throw new MissingSellerIdException();
        }
    }

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
