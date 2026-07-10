package com.ecommerce.product.controller;

import com.ecommerce.product.service.FileStorageService;
import com.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 자동완성 keyword 최소 길이 검증 (M3) 등 컨트롤러 진입 검증 단위 테스트 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController 단위 테스트")
class ProductControllerTest {

    @Mock private ProductService productService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private ProductController productController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    @DisplayName("자동완성 — keyword 미전달 시 빈 목록 (서비스 미호출)")
    void suggestions_noKeyword_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(productService, never()).suggestNames(anyString(), anyInt());
    }

    @Test
    @DisplayName("자동완성 — 공백만 keyword 시 빈 목록 (서비스 미호출)")
    void suggestions_blankKeyword_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(productService, never()).suggestNames(anyString(), anyInt());
    }

    @Test
    @DisplayName("자동완성 — 최소 길이 이상 keyword 시 서비스 위임")
    void suggestions_validKeyword_delegates() throws Exception {
        org.mockito.BDDMockito.given(productService.suggestNames("갤럭시", 10))
                .willReturn(List.of("갤럭시 S24"));

        mockMvc.perform(get("/api/v1/products/suggestions").param("keyword", "갤럭시"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"갤럭시 S24\"]"));

        verify(productService).suggestNames("갤럭시", 10);
    }
}
