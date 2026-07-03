package com.ecommerce.product.controller;

import com.ecommerce.product.domain.SortOption;
import com.ecommerce.product.dto.request.CreateProductRequest;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import com.ecommerce.product.dto.request.UpdateProductRequest;
import com.ecommerce.product.dto.response.ImageUploadResponse;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.exception.InvalidSearchParameterException;
import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.ProductService;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    /** 자동완성 후보 기본/최대 개수 */
    private static final int SUGGESTION_DEFAULT_LIMIT = 10;
    private static final int SUGGESTION_MAX_LIMIT = 20;
    /** 자동완성 keyword 최소 길이 — trim 후 이 값 미만이면 빈 목록 (넓은 prefix 매칭 부하 방지) */
    private static final int SUGGESTION_MIN_KEYWORD_LENGTH = 1;

    private final ProductService productService;
    private final FileStorageService fileStorageService;

    /** 상품 등록 (ADMIN/SELLER) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestHeader(value = "X-User-Id",   required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateProductRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request, userId, role));
    }

    /** 상품 수정 (ADMIN: 전체, SELLER: 본인) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @RequestHeader(value = "X-User-Id",   required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request, userId, role));
    }

    /** 상품 삭제 (ADMIN: 전체, SELLER: 본인) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader(value = "X-User-Id",   required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id
    ) {
        productService.deleteProduct(id, userId, role);
        return ResponseEntity.noContent().build();
    }

    /** 판매 금지 (ADMIN 전용) */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/ban")
    public ResponseEntity<Void> banProduct(@PathVariable Long id) {
        productService.banProduct(id);
        return ResponseEntity.noContent().build();
    }

    /** 판매 금지 해제 (ADMIN 전용) */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unban")
    public ResponseEntity<Void> unbanProduct(@PathVariable Long id) {
        productService.unbanProduct(id);
        return ResponseEntity.noContent().build();
    }

    /** 상품 이미지 업로드 → MinIO 저장 후 URL 반환 (ADMIN/SELLER) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PostMapping("/upload-image")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        String url = fileStorageService.uploadImage(file);
        return ResponseEntity.ok(new ImageUploadResponse(url));
    }

    /** 내 상품 목록 (SELLER 전용) */
    @GetMapping("/mine")
    public ResponseEntity<Page<ProductSummaryResponse>> getMyProducts(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(productService.getMyProducts(userId, pageable));
    }

    /**
     * 상품 목록 조회.
     * 공개 목록은 항상 판매 금지 상품 제외 (보는 사람이 ADMIN이어도).
     * 관리자 화면은 includeBanned=true를 명시해야 금지 상품 포함 + 판매자 정보 노출 (ADMIN 한정).
     */
    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> findProducts(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "false") boolean includeBanned,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        validatePriceRange(minPrice, maxPrice);
        // sort 미전달/미허용 값은 화이트리스트에서 latest로 폴백 (하위호환)
        SortOption sortOption = SortOption.from(sort);
        // 관리자 화면 전용 뷰: ADMIN이 명시적으로 요청할 때만 (금지 상품 포함 + 판매자 정보)
        boolean adminView = "ADMIN".equals(role) && includeBanned;
        ProductSearchRequest request =
                new ProductSearchRequest(categoryId, keyword, minPrice, maxPrice, sortOption, pageable);
        return ResponseEntity.ok(productService.findProducts(request, adminView));
    }

    /** 상품명 자동완성 후보 (판매중 상품 한정) */
    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> suggestProductNames(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "" + SUGGESTION_DEFAULT_LIMIT) int limit
    ) {
        // keyword trim 후 최소 길이 미만(공백만 포함)이면 빈 목록 반환 (진입 검증)
        if (keyword == null || keyword.trim().length() < SUGGESTION_MIN_KEYWORD_LENGTH) {
            return ResponseEntity.ok(List.of());
        }
        int cappedLimit = Math.min(Math.max(limit, 1), SUGGESTION_MAX_LIMIT);
        return ResponseEntity.ok(productService.suggestNames(keyword, cappedLimit));
    }

    /** 가격대 검증 — 음수·역전 시 400 (Controller 진입 검증) */
    private void validatePriceRange(Long minPrice, Long maxPrice) {
        if (minPrice != null && minPrice < 0) {
            throw new InvalidSearchParameterException("minPrice는 0 이상이어야 합니다.");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new InvalidSearchParameterException("maxPrice는 0 이상이어야 합니다.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new InvalidSearchParameterException("minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }

    /** 상품 상세 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }
}
