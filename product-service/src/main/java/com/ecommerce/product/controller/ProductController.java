package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.CreateProductRequest;
import com.ecommerce.product.dto.request.ProductSearchRequest;
import com.ecommerce.product.dto.request.UpdateProductRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.ProductSummaryResponse;
import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.ProductService;
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

    /** 상품 이미지 업로드 → MinIO 저장 후 URL 반환 (ADMIN/SELLER) */
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @PostMapping("/upload-image")
    public ResponseEntity<java.util.Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        String url = fileStorageService.uploadImage(file);
        return ResponseEntity.ok(java.util.Map.of("url", url));
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

    /** 상품 목록 조회 — ADMIN 요청 시 판매자명/이메일 포함 */
    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> findProducts(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        boolean enrichSeller = "ADMIN".equals(role);
        return ResponseEntity.ok(productService.findProducts(
                new ProductSearchRequest(categoryId, keyword, pageable), enrichSeller
        ));
    }

    /** 상품 상세 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }
}
