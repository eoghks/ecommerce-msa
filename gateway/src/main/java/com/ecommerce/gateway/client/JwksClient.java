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
 *
 * [왜 @Scheduled가 필요한가]
 * 현재 auth-service는 기동 시마다 RSA 키를 새로 생성한다 (JwtProvider.@PostConstruct).
 * auth-service가 재시작되면 새 키로 발급된 토큰을 Gateway가 가진 옛날 공개키로 검증 → 401 발생.
 * @Scheduled(5분)로 주기적으로 공개키를 재fetch하여 최대 5분 내 자동 복구.
 *
 * [근본 해결책]
 * auth-service RSA 키를 외부(K8s Secret / Vault)에서 고정 PEM으로 주입하면
 * 재시작해도 키가 바뀌지 않으므로 @Scheduled 불필요.
 * → docs/tradeoffs/CR-01-rsa-key-inmemory.md 참고
 *
 * [volatile 이유]
 * @Scheduled(스케줄러 스레드)가 publicKey를 갱신할 때 요청 처리 스레드가
 * CPU 캐시의 옛날 값을 읽지 않도록 메인 메모리 직접 읽기/쓰기 보장.
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
