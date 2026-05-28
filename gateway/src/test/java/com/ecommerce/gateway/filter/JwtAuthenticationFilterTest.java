package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.client.JwksClient;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private JwksClient jwksClient;

    private RSAPublicKey  publicKey;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        publicKey  = (RSAPublicKey)  pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        jwksClient = mock(JwksClient.class);
        when(jwksClient.getPublicKey()).thenReturn(publicKey);

        filter = new JwtAuthenticationFilter(jwksClient);
        chain  = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ── 화이트리스트 ──────────────────────────────────────────

    @Test
    @DisplayName("화이트리스트 경로는 토큰 없이 통과")
    void whitelist_passthrough_without_token() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── 헤더 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("인증 필수 경로에서 Authorization 헤더 없으면 401 반환")
    void no_auth_header_returns_401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 필수 경로에서 Bearer 접두사 없으면 401 반환")
    void invalid_token_format_returns_401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "InvalidToken abc")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("선택 인증 경로(상품 목록)는 토큰 없이 통과")
    void optional_auth_path_passes_without_token() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── RSA 서명 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("유효한 JWT - 필터 통과 및 X-User-Id / X-User-Role 헤더 주입")
    void valid_jwt_passes_and_injects_headers() throws Exception {
        String token = issueToken(1L, "USER", new Date(System.currentTimeMillis() + 3600000L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("만료된 JWT - 401 반환")
    void expired_jwt_returns_401() throws Exception {
        String token = issueToken(1L, "USER", new Date(System.currentTimeMillis() - 1000L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("다른 키로 서명된 JWT - 401 반환")
    void wrong_signature_returns_401() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey otherPrivateKey = (RSAPrivateKey) gen.generateKeyPair().getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("1")
                .claim("role", "USER")
                .expirationTime(new Date(System.currentTimeMillis() + 3600000L))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(otherPrivateKey));
        String token = jwt.serialize();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("공개키 미로드 상태 - 401 반환")
    void null_public_key_returns_401() throws Exception {
        when(jwksClient.getPublicKey()).thenReturn(null);
        String token = issueToken(1L, "USER", new Date(System.currentTimeMillis() + 3600000L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── 기타 ──────────────────────────────────────────

    @Test
    @DisplayName("필터 우선순위는 -1 (가장 먼저 실행)")
    void filter_order_is_negative_one() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    // ── 헬퍼 ──────────────────────────────────────────

    private String issueToken(Long userId, String role, Date expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issueTime(new Date())
                .expirationTime(expiry)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}
