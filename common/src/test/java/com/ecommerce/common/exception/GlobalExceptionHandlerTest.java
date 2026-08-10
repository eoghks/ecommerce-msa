package com.ecommerce.common.exception;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // 각 서비스가 사용하는 매퍼와 동일하게 "알 수 없는 필드 거부"를 켠 상태로 검증 (F-01)
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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

    // ── F-01/F-05: 요청 바인딩 오류는 500이 아닌 400/404/413 ────────────

    @Test
    @DisplayName("깨진 JSON → 400 (본문 내용 미노출)")
    void malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"))
                .andExpect(jsonPath("$.detail").value("요청 본문을 해석할 수 없습니다. 형식과 값을 확인해주세요."));
    }

    /** 각 서비스는 raw ObjectMapper 빈을 사용해 알 수 없는 필드를 거부하므로 동일 설정으로 검증 (UT-CART-005) */
    @Test
    @DisplayName("알 수 없는 JSON 필드 → 400")
    void unknown_json_field_returns_400() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"price\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"));
    }

    @Test
    @DisplayName("요청 파라미터 타입 불일치 → 400")
    void type_mismatch_returns_400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Parameter"));
    }

    @Test
    @DisplayName("필수 파라미터 누락 → 400")
    void missing_parameter_returns_400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Parameter"));
    }

    @Test
    @DisplayName("multipart 파일 파라미터 누락 → 400")
    void missing_request_part_returns_400() throws Exception {
        mockMvc.perform(multipart("/test/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Request Part"));
    }

    @Test
    @DisplayName("업로드 용량 초과 → 413")
    void max_upload_size_exceeded_returns_413() throws Exception {
        mockMvc.perform(get("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("Payload Too Large"));
    }

    @Test
    @DisplayName("매핑되지 않은 경로 → 404 (요청 경로 미노출)")
    void no_resource_found_returns_404() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("요청하신 리소스를 찾을 수 없습니다."));
    }

    // ── 테스트용 컨트롤러 ──────────────────────────────────────────
    @RestController
    static class TestController {

        /** 업로드 용량 초과 상한값 — 예외 생성용 더미 */
        private static final long MAX_UPLOAD_SIZE = 10L;

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

        @GetMapping("/test/type-mismatch")
        public void typeMismatch(@RequestParam("size") int size) {}

        @PostMapping("/test/upload")
        public void upload(@RequestPart("file") MultipartFile file) {}

        @GetMapping("/test/too-large")
        public void tooLarge() {
            throw new MaxUploadSizeExceededException(MAX_UPLOAD_SIZE);
        }

        @GetMapping("/test/no-resource")
        public void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/no-resource");
        }

        record TestRequest(@NotBlank String name) {}
    }
}
