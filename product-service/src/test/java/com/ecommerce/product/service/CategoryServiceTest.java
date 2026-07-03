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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService 단위 테스트")
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private CategoryService categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().name("전자기기").build();
        ReflectionTestUtils.setField(category, "id", 1L);
    }

    // ── createCategory ───────────────────────────────────────────

    @Test
    @DisplayName("카테고리 등록 — 정상")
    void createCategory_success() {
        CategoryCreateRequest request = new CategoryCreateRequest("의류");
        given(categoryRepository.existsByName("의류")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(inv -> {
            Category c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 2L);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.name()).isEqualTo("의류");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 등록 — 중복 이름 거부")
    void createCategory_duplicate() {
        CategoryCreateRequest request = new CategoryCreateRequest("전자기기");
        given(categoryRepository.existsByName("전자기기")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(categoryRepository, never()).save(any());
    }

    // ── updateCategory ───────────────────────────────────────────

    @Test
    @DisplayName("카테고리 수정 — 정상")
    void updateCategory_success() {
        CategoryUpdateRequest request = new CategoryUpdateRequest("가전");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("가전")).willReturn(false);

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.name()).isEqualTo("가전");
    }

    @Test
    @DisplayName("카테고리 수정 — 존재하지 않는 카테고리 예외")
    void updateCategory_notFound() {
        CategoryUpdateRequest request = new CategoryUpdateRequest("가전");
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(99L, request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("카테고리 수정 — 다른 카테고리와 이름 중복 거부")
    void updateCategory_duplicate() {
        CategoryUpdateRequest request = new CategoryUpdateRequest("의류");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("의류")).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @DisplayName("카테고리 수정 — 이름 변경 없으면 중복 검사 생략")
    void updateCategory_sameName() {
        CategoryUpdateRequest request = new CategoryUpdateRequest("전자기기");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.name()).isEqualTo("전자기기");
        verify(categoryRepository, never()).existsByName(any());
    }

    // ── deleteCategory ───────────────────────────────────────────

    @Test
    @DisplayName("카테고리 삭제 — 정상 (참조 상품 없음)")
    void deleteCategory_success() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.countByCategoryId(1L)).willReturn(0L);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("카테고리 삭제 — 참조 상품 존재 시 거부")
    void deleteCategory_inUse() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.countByCategoryId(1L)).willReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(CategoryInUseException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("카테고리 삭제 — 존재하지 않는 카테고리 예외")
    void deleteCategory_notFound() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(99L))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepository, never()).delete(any());
    }
}
