package com.ecommerce.gateway.client;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.interfaces.RSAPublicKey;

/**
 * Auth Service JWKS 엔드포인트에서 RSA 공개키를 fetch하여 캐싱.
 * Gateway 기동 시 1회 로드, 이후 인메모리 캐싱.
 */
@Slf4j
@Component
public class JwksClient {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 2000L;

    @Value("${auth.jwks-url:http://localhost:8081/api/v1/auth/.well-known/jwks.json}")
    private String jwksUrl;

    private RSAPublicKey publicKey;

    @PostConstruct
    public void loadPublicKey() {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String json = WebClient.create()
                        .get()
                        .uri(jwksUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JWKSet jwkSet = JWKSet.parse(json);
                RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
                this.publicKey = rsaKey.toRSAPublicKey();
                log.info("JWKS 공개키 로드 완료 (시도 {}회)", attempt);
                return;
            } catch (Exception e) {
                log.warn("JWKS 공개키 로드 실패 (시도 {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.warn("JWKS 공개키 로드 최종 실패 - JWT 검증 불가 상태. Auth Service 기동 후 재시작 필요.");
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
