package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.client.JwksClient;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/.well-known",
            "/actuator"
    );

    private final JwksClient jwksClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("인증 헤더 없음: path={}", path);
            return onUnauthorized(exchange);
        }

        RSAPublicKey publicKey = jwksClient.getPublicKey();
        if (publicKey == null) {
            log.warn("공개키 미로드 — Auth Service 연결 확인 필요: path={}", path);
            return onUnauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);

            if (!jwt.verify(verifier)) {
                log.warn("JWT 서명 검증 실패: path={}", path);
                return onUnauthorized(exchange);
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                log.warn("JWT 만료: path={}", path);
                return onUnauthorized(exchange);
            }

            String userId = claims.getSubject();
            String role   = claims.getStringClaim("role");

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id",   userId)
                    .header("X-User-Role", role)
                    .build();

            log.debug("JWT 검증 성공: userId={}, role={}, path={}", userId, role, path);
            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (Exception e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
            return onUnauthorized(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
