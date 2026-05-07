package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 인증 글로벌 필터
 * - WHITE_LIST 경로는 인증 생략
 * - 나머지 경로는 Authorization: Bearer <token> 헤더 필수
 *
 * TODO (Week 2): Auth Service RSA 공개키로 JWT 서명 검증 완성
 * TODO (Week 2): 검증 후 X-User-Id / X-User-Role 헤더를 하위 서비스에 전달
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    // 인증 불필요 경로 (화이트리스트)
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/actuator"
    );

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

        // TODO (Week 2): nimbus-jose-jwt 로 RSA 서명 검증
        // String token = authHeader.substring(BEARER_PREFIX.length());
        // try {
        //     SignedJWT jwt = jwtProvider.validate(token);
        //     String userId = jwt.getJWTClaimsSet().getSubject();
        //     String role   = jwt.getJWTClaimsSet().getStringClaim("role");
        //     ServerHttpRequest mutated = exchange.getRequest().mutate()
        //             .header("X-User-Id", userId)
        //             .header("X-User-Role", role)
        //             .build();
        //     return chain.filter(exchange.mutate().request(mutated).build());
        // } catch (Exception e) {
        //     log.warn("JWT 검증 실패: {}", e.getMessage());
        //     return onUnauthorized(exchange);
        // }

        // [Week 1 스텁] 헤더 존재 여부만 확인, 서명 검증 미완성
        log.debug("JWT 헤더 확인 완료 (서명 검증은 Week 2 완성): path={}", path);
        return chain.filter(exchange);
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
        return -1; // 모든 필터 중 가장 먼저 실행
    }
}
