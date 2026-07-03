package com.ecommerce.product.service;

import com.ecommerce.product.domain.Category;
import com.ecommerce.product.dto.request.CategoryCreateRequest;
import com.ecommerce.product.dto.request.CategoryUpdateRequest;
import com.ecommerce.product.dto.response.CategoryResponse;
import com.ecommerce.product.exception.CategoryInUseException;
import com.ecommerce.product.exception.CategoryNotFoundException;
import com.ecommerce.product.exception.DuplicateCategoryNameException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /** 카테고리 목록 조회 (전체 공개) */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** 카테고리 등록 (ADMIN) — 이름 중복 거부 */
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        String name = request.name().trim();
        requireUniqueName(name);
        Category saved = categoryRepository.save(Category.builder().name(name).build());
        return CategoryResponse.from(saved);
    }

    /** 카테고리 수정 (ADMIN) — 이름 변경, 중복 거부 */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = loadCategory(id);
        String name = request.name().trim();
        if (!category.getName().equals(name)) {
            requireUniqueName(name);
            category.updateName(name);
        }
        return CategoryResponse.from(category);
    }

    /** 카테고리 삭제 (ADMIN) — 참조 상품 존재 시 거부 */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = loadCategory(id);
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new CategoryInUseException(id, productCount);
        }
        categoryRepository.delete(category);
    }

    // ── private helpers ──────────────────────────────────────────

    private void requireUniqueName(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new DuplicateCategoryNameException(name);
        }
    }

    private Category loadCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
