# Rate Limiting (구현 보류 — 학습 메모)

## 상태
- **현재**: 미구현
- **사유**: 토이프로젝트 트래픽 환경에서 효과 검증 불가, 우선순위 낮음
- **재검토 시점**: Week 6 마무리에 시간 여유 있을 때 또는 운영 시뮬레이션 단계

---

## 구현 전략

Spring Cloud Gateway 내장 `RequestRateLimiter` + `RedisRateLimiter` (Token Bucket).

### 정책
| 대상 | 기준 | 제한 |
|------|------|------|
| 비인증 사용자 | IP | 분당 30req |
| 인증 사용자 | userId (JWT subject) | 분당 100req |
| 관리자 | userId | 분당 300req |

초과 시 `429 Too Many Requests`.

---

## 구현 단계

### 1. 의존성 (gateway/build.gradle)
```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
```

### 2. KeyResolver 정의
```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            String ip = (xff != null)
                    ? xff.split(",")[0].trim()
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.justOrEmpty(userId).switchIfEmpty(Mono.just("anonymous"));
        };
    }
}
```

### 3. 라우팅 설정 (application.yml)
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1     # 초당 1개 충전
                redis-rate-limiter.burstCapacity: 10    # 최대 10개 버스트
                key-resolver: "#{@ipKeyResolver}"

        - id: product-service
          uri: http://product-service:8082
          predicates:
            - Path=/api/products/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 2
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@userKeyResolver}"
```

---

## 핵심 개념: Token Bucket

| 파라미터 | 의미 |
|----------|------|
| `replenishRate` | 초당 몇 개 토큰 충전 (= 평균 허용 RPS) |
| `burstCapacity` | 버킷 최대 용량 (= 순간 버스트 허용량) |

예: `replenishRate=2`, `burstCapacity=20` → 평균 분당 120req, 단기간 20req 버스트 허용.

---

## 운영 시 주의사항

### 1. 프록시 환경 IP 추출
- Gateway 앞에 Nginx/ALB 있으면 `getRemoteAddress()` 가 프록시 IP 반환
- **`X-Forwarded-For` 헤더 우선 사용** 필수
- 단, 클라이언트가 직접 `X-Forwarded-For` 위변조 가능 → 신뢰할 프록시만 통과시키는 설정 필요

### 2. Redis 키 충돌
- 기본 키: `request_rate_limiter.{key}.tokens`
- 여러 환경이 동일 Redis 공유하면 prefix 분리 필요

### 3. 응답 헤더
- 자동 추가: `X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`, `X-RateLimit-Replenish-Rate`
- 클라이언트가 남은 쿼터 인지 가능

### 4. Distributed Rate Limiting
- Redis 기반이므로 Gateway 인스턴스 여러 대여도 동일 정책 적용됨
- Gateway 무상태 + Redis 중앙 집계 = 자연스러운 분산 처리

---

## 면접 답변 시나리오

> Q: Rate Limiting 은 왜 안 넣었나요?
> A: 토이프로젝트 트래픽 환경에선 효과 검증이 안 돼서 우선순위를 낮췄습니다. 다만 운영 환경에선 Spring Cloud Gateway + Redis 기반 Token Bucket 으로 구현할 계획이고, IP/userId 별 정책 분리, X-Forwarded-For 처리, 분산 Redis 키 관리까지 설계해뒀습니다. 문서화도 해놨습니다.

---

## 참고 자료
- [Spring Cloud Gateway — RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#the-requestratelimiter-gatewayfilter-factory)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)
