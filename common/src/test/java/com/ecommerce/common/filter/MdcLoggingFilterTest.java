package com.ecommerce.common.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("X-Request-ID 헤더 없으면 UUID 자동 생성 후 응답 헤더에 포함")
    void generates_uuid_when_no_header() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isNotNull();
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    }

    @Test
    @DisplayName("X-Request-ID 헤더 있으면 그대로 재사용")
    void reuses_existing_request_id() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/test");
        request.addHeader("X-Request-ID", "my-fixed-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("my-fixed-id");
    }

    @Test
    @DisplayName("요청 종료 후 MDC 초기화 — 스레드풀 오염 방지")
    void clears_mdc_after_request() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("빈 X-Request-ID 헤더는 무시하고 UUID 신규 생성")
    void ignores_blank_request_id_header() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/test");
        request.addHeader("X-Request-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain         chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("   ");
    }
}
