package com.ecommerce.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 ProblemDetail")
    void illegalArgument_returns_400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("NoSuchElementException → 404 ProblemDetail")
    void noSuchElement_returns_404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("@Valid 검증 실패 → 400 + errors[] 포함")
    void validation_failure_returns_400_with_errors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("처리되지 않은 Exception → 500 ProblemDetail")
    void unhandled_exception_returns_500() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }

    // ── 테스트용 컨트롤러 ──────────────────────────────────────────
    @RestController
    static class TestController {

        @GetMapping("/test/bad-request")
        public void badRequest() {
            throw new IllegalArgumentException("잘못된 요청");
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new NoSuchElementException("리소스 없음");
        }

        @PostMapping("/test/validation")
        public void validation(@Valid @RequestBody TestRequest req) {}

        @GetMapping("/test/error")
        public void error() {
            throw new RuntimeException("예상치 못한 오류");
        }

        record TestRequest(@NotBlank String name) {}
    }
}
