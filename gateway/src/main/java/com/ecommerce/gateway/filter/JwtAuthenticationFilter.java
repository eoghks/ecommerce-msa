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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
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

    private static final String BEARER_PREFIX    = "Bearer ";
    private static final String HEADER_USER_ID   = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    // logout은 JWT 검증 후 X-User-Id로 Redis 토큰 전체 삭제 — whitelist 제외
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/check-email",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/.well-known",
            "/actuator/"
    );

    // 토큰 없이도 접근 허용 (있으면 검증 후 헤더 주입)
    private static final List<String> OPTIONAL_AUTH_LIST = List.of(
            "/api/v1/products",
            "/api/v1/categories"
    );

    private final JwksClient jwksClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean isOptional = isOptionalAuth(path);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            if (isOptional) {
                // 토큰 없어도 통과 (비로그인 상품 조회 허용)
                return chain.filter(exchange);
            }
            log.warn("인증 헤더 없음: path={}", path);
            return onUnauthorized(exchange);
        }

        RSAPublicKey publicKey = jwksClient.getPublicKey();
        if (publicKey == null) {
            // 토큰을 제공했는데 공개키 미로드 — optional 경로여도 검증 불가이므로 401
            log.warn("공개키 미로드 — Auth Service 연결 확인 필요: path={}", path);
            return onUnauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                // 토큰을 제공했는데 서명 불일치 — optional 경로여도 401
                log.warn("JWT 서명 검증 실패: path={}", path);
                return onUnauthorized(exchange);
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // HR-02: exp 클레임 null 방어
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                // 토큰을 제공했는데 만료 — optional 경로여도 401
                log.warn("JWT 만료 또는 exp 클레임 없음: path={}", path);
                return onUnauthorized(exchange);
            }

            String userId = claims.getSubject();
            String role   = claims.getStringClaim("role");

            // HR-04: 클라이언트 위조 헤더 제거 후 재설정
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove(HEADER_USER_ID);
                        h.remove(HEADER_USER_ROLE);
                    })
                    .header(HEADER_USER_ID,   userId)
                    .header(HEADER_USER_ROLE, role)
                    .build();

            // SecurityContext 등록 — Spring Security(actuator 권한 체크 등)가 role을 인식하도록
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );

            log.debug("JWT 검증 성공: userId={}, role={}, path={}", userId, role, path);
            return chain.filter(exchange.mutate().request(mutated).build())
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

        } catch (Exception e) {
            // 토큰을 제공했는데 파싱 실패 — optional 경로여도 401
            log.warn("JWT 검증 실패: {}", e.getMessage());
            return onUnauthorized(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private boolean isOptionalAuth(String path) {
        return OPTIONAL_AUTH_LIST.stream().anyMatch(path::startsWith);
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
