package com.ecommerce.product.controller;

import com.ecommerce.product.domain.Category;
import com.ecommerce.product.dto.request.CategoryCreateRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.repository.CategoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    /** 카테고리 목록 조회 (전체 공개) */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(
                categoryRepository.findAll().stream()
                        .map(CategoryResponse::from)
                        .toList()
        );
    }

    /** 카테고리 등록 (ADMIN 전용) */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        Category saved = categoryRepository.save(Category.builder().name(request.name()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(saved));
    }
}
