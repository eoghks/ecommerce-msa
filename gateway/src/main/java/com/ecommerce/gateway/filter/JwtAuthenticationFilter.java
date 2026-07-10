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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
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
            "/api/v1/categories",
            "/api/v1/cart"      // 비로그인 장바구니 허용 (게스트: 쿠키 기반)
    );

    // 서비스 간 직접 호출(내부망) 전용 — 게이트웨이(외부)로는 접근 차단
    private static final List<String> INTERNAL_ONLY_LIST = List.of(
            "/api/v1/auth/users",           // H-1: 판매자 PII 배치 조회 (product→auth 내부 전용)
            "/api/v1/orders/internal"       // V1.1-1: 구매 인증 조회 (product→order 내부 전용)
    );

    private final JwksClient jwksClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // C-1: 진입 즉시 클라이언트가 위조했을 수 있는 신뢰 헤더(X-User-*)를 무조건 제거.
        //      이후 모든 통과 분기는 sanitize된 요청을 사용하고, JWT 검증 성공 시에만 재주입한다.
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                })
                .build();
        ServerWebExchange sanitized = exchange.mutate().request(sanitizedRequest).build();

        // H-1: 내부 전용 엔드포인트는 외부(게이트웨이) 접근 차단
        if (isInternalOnly(path)) {
            log.warn("내부 전용 엔드포인트 외부 접근 차단: path={}", path);
            return onForbidden(sanitized);
        }

        if (isWhitelisted(path)) {
            return chain.filter(sanitized);
        }

        String authHeader = sanitized.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean isOptional = isOptionalAuth(path);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            if (isOptional) {
                // 토큰 없어도 통과 (비로그인 상품 조회 허용) — 단 헤더는 이미 제거됨
                return chain.filter(sanitized);
            }
            log.warn("인증 헤더 없음: path={}", path);
            return onUnauthorized(sanitized);
        }

        RSAPublicKey publicKey = jwksClient.getPublicKey();
        if (publicKey == null) {
            // 토큰을 제공했는데 공개키 미로드 — optional 경로여도 검증 불가이므로 401
            log.warn("공개키 미로드 — Auth Service 연결 확인 필요: path={}", path);
            return onUnauthorized(sanitized);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                log.warn("JWT 서명 검증 실패: path={}", path);
                return onUnauthorized(sanitized);
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // HR-02: exp 클레임 null 방어
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                log.warn("JWT 만료 또는 exp 클레임 없음: path={}", path);
                return onUnauthorized(sanitized);
            }

            String userId = claims.getSubject();
            String role   = claims.getStringClaim("role");

            // 검증 성공 시에만 신뢰 헤더 주입 (sanitize된 요청 기준)
            ServerHttpRequest mutated = sanitizedRequest.mutate()
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
            log.warn("JWT 검증 실패: {}", e.getMessage());
            return onUnauthorized(sanitized);
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private boolean isOptionalAuth(String path) {
        return OPTIONAL_AUTH_LIST.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalOnly(String path) {
        return INTERNAL_ONLY_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "인증이 필요합니다.");
    }

    private Mono<Void> onForbidden(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.FORBIDDEN, "forbidden", "접근 권한이 없습니다.");
    }

    // 에러 응답을 간단한 JSON 바디로 반환 (빈 본문 방지)
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
                                  String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
