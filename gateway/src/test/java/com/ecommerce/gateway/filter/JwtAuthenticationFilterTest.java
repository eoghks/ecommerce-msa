package com.ecommerce.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("화이트리스트 경로는 토큰 없이 통과")
    void whitelist_passthrough_without_token() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 401 반환")
    void no_auth_header_returns_401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/products")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer 접두사 없으면 401 반환")
    void invalid_token_format_returns_401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "InvalidToken abc")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer 토큰 있으면 필터 통과 (Week 1 스텁 — 서명 검증 미완성)")
    void bearer_token_passes_stub_filter() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer stub-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("필터 우선순위는 -1 (가장 먼저 실행)")
    void filter_order_is_negative_one() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
