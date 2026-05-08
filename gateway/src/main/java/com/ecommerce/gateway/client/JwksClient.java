package com.ecommerce.gateway.client;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.interfaces.RSAPublicKey;

/**
 * Auth Service JWKS 엔드포인트에서 RSA 공개키를 fetch·캐싱.
 * - 기동 시 1회 로드 (@PostConstruct)
 * - CR-03: 주기적 갱신 (@Scheduled) — auth-service 재시작 후 키 변경 대응
 * - MD-04: volatile 필드로 멀티스레드 가시성 보장
 */
@Slf4j
@Component
public class JwksClient {

    private static final int  MAX_RETRY      = 3;
    private static final long RETRY_DELAY_MS = 2000L;

    @Value("${auth.jwks-url:http://localhost:8081/api/v1/auth/.well-known/jwks.json}")
    private String jwksUrl;

    // MD-04: volatile — @Scheduled 갱신 시 가시성 보장
    private volatile RSAPublicKey publicKey;

    private final WebClient webClient = WebClient.create();

    @PostConstruct
    public void loadPublicKey() {
        refreshPublicKey();
    }

    // CR-03: 주기적 공개키 갱신 (기본 5분, 환경변수로 조정 가능)
    @Scheduled(fixedDelayString = "${auth.jwks-refresh-interval-ms:300000}")
    public void refreshPublicKey() {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String json = webClient.get()
                        .uri(jwksUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                this.publicKey = ((RSAKey) JWKSet.parse(json).getKeys().get(0)).toRSAPublicKey();
                log.info("JWKS 공개키 로드 완료 (시도 {}회)", attempt);
                return;
            } catch (Exception e) {
                log.warn("JWKS 공개키 로드 실패 (시도 {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.warn("JWKS 공개키 로드 최종 실패. Auth Service 기동 여부 확인 필요.");
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
