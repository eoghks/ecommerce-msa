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
 * [기동 순서 문제 해결]
 * Gateway가 Auth-service보다 먼저 뜨면 @PostConstruct 시점에 JWKS 로드 실패 → publicKey == null.
 * 이를 해결하기 위해 두 단계 스케줄러를 운영한다:
 *   1) fastRecovery: 5초 간격 — publicKey가 null인 동안만 재시도. Auth-service 기동 후 즉시 복구.
 *   2) refreshPublicKey: 5분 간격 — 정상 운영 중 키 교체(auth-service 재시작) 대응.
 *
 * [근본 해결책]
 * auth-service RSA 키를 외부(K8s Secret / Vault)에서 고정 PEM으로 주입하면
 * 재시작해도 키가 바뀌지 않으므로 두 스케줄러 모두 불필요.
 * → docs/tradeoffs/CR-01-rsa-key-inmemory.md 참고
 *
 * [volatile 이유]
 * 스케줄러 스레드가 publicKey를 갱신할 때 요청 처리 스레드가
 * CPU 캐시의 옛날 값을 읽지 않도록 메인 메모리 직접 읽기/쓰기 보장.
 */
@Slf4j
@Component
public class JwksClient {

    private static final int  MAX_RETRY      = 3;
    private static final long RETRY_DELAY_MS = 2000L;

    @Value("${auth.jwks-url:http://localhost:8081/api/v1/auth/.well-known/jwks.json}")
    private String jwksUrl;

    // MD-04: volatile — 스케줄러 스레드 갱신 시 가시성 보장
    private volatile RSAPublicKey publicKey;

    private final WebClient webClient = WebClient.create();

    @PostConstruct
    public void loadPublicKey() {
        refreshPublicKey();
    }

    /**
     * 빠른 복구 스케줄러 — publicKey가 null인 동안 5초마다 재시도.
     * Auth-service가 늦게 기동되어 초기 로드가 실패한 경우 자동 복구.
     * publicKey가 정상이면 즉시 반환하므로 오버헤드 없음.
     */
    @Scheduled(fixedDelay = 5000)
    public void fastRecovery() {
        if (publicKey == null) {
            log.info("공개키 미로드 상태 — 빠른 복구 재시도 중");
            refreshPublicKey();
        }
    }

    // CR-03: 주기적 공개키 갱신 — auth-service 재시작 시 키 교체 대응 (기본 5분)
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
