package com.ecommerce.product.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Auth Service에서 판매자(사용자) 요약 정보를 조회.
 * 상품의 sellerId → 판매자명/이메일 매핑용 (ADMIN 화면 전용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserClient {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestTemplate restTemplate;

    @Value("${service.auth.url:http://localhost:8081}")
    private String authServiceUrl;

    // M-N1: auth-service와 공유하는 내부 호출 시크릿
    @Value("${app.internal.token:dev-internal-secret}")
    private String internalToken;

    /** sellerId 목록 → {id: UserSummary} 맵. 실패 시 빈 맵 반환 (조회 실패가 상품 목록을 막지 않도록) */
    public Map<Long, UserSummary> getUsersByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl + "/api/v1/auth/users")
                    .queryParam("ids", ids.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    .build().toUriString();

            // 내부 인증 헤더 첨부 (auth-service가 X-Internal-Token 검증)
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_INTERNAL_TOKEN, internalToken);

            List<UserSummary> users = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<UserSummary>>() {}
            ).getBody();

            if (users == null) return Map.of();
            return users.stream().collect(Collectors.toMap(UserSummary::id, Function.identity()));
        } catch (Exception ex) {
            log.warn("Auth Service 판매자 조회 실패 — 판매자 정보 없이 진행: {}", ex.getMessage());
            return Map.of();
        }
    }

    public record UserSummary(Long id, String name, String email) {}
}
