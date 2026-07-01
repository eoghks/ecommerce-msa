# Code Review — auth-service / gateway

**검토일:** 2026-05-07  
**대상:** `auth-service` · `gateway` (Java 21 / Spring Boot 3.5.0 / nimbus-jose-jwt 9.40)  
**심각도 기준:** CRITICAL → HIGH → MEDIUM → LOW

---

## 요약

| 심각도   | 건수 |
|----------|------|
| CRITICAL | 3    |
| HIGH     | 5    |
| MEDIUM   | 6    |
| LOW      | 4    |
| **합계** | **18** |

전반적으로 레이어 분리와 Jakarta EE 전환은 잘 되어 있다.  
주요 위험은 세 가지다: RSA 키 페어의 인스턴스 내 휘발(재시작 시 모든 액세스 토큰 무효화), Refresh Token Rotation의 TOCTOU 레이스 컨디션, 그리고 JWKS 공개키의 런타임 갱신 불가로 인한 무중단 운영 불가.

---

## CRITICAL

### CR-01 · RSA 키 페어 인메모리 생성 — 재시작 시 전체 Access Token 무효화

**파일:** `auth-service/.../jwt/JwtProvider.java:38-43`

**문제:**  
애플리케이션 기동 시 `KeyPairGenerator`로 RSA 키 페어를 새로 생성한다. 재시작 또는 스케일 아웃 시 인스턴스마다 공개키가 달라지므로, 기존 Access Token은 다른 인스턴스(또는 재시작 후 동일 인스턴스)에서 서명 검증에 실패한다. 또한 Gateway는 기동 시 1회 JWKS를 fetch해 캐싱하므로, auth-service 재시작 후 키가 바뀌면 Gateway가 보유한 공개키와 불일치해 모든 요청이 401이 된다.

**수정 방향:**
```java
// application.yml 또는 K8s Secret으로 PEM 주입
@Value("${jwt.private-key-pem}")
private String privateKeyPem;

@Value("${jwt.public-key-pem}")
private String publicKeyPem;

@PostConstruct
public void init() throws Exception {
    // PEM → PKCS8 디코딩
    byte[] keyBytes = Base64.getDecoder().decode(
        privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                     .replace("-----END PRIVATE KEY-----", "")
                     .replaceAll("\\s", ""));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    // publicKey도 동일 방식
}
```

---

### CR-02 · Refresh Token Rotation — TOCTOU 레이스 컨디션

**파일:** `auth-service/.../service/AuthService.java:61-74`

**문제:**  
`refresh()` 메서드는 `@Transactional(readOnly = true)` 이다. Redis 연산(`delete` → `save`)은 JPA 트랜잭션 범위 밖에서 실행되고, 두 Redis 연산 사이에 원자성이 없다. 동일한 Refresh Token으로 동시 요청이 오면 두 요청 모두 `findUserIdByToken`을 통과하고, 각각 `delete`→`save`를 실행해 두 개의 새 토큰이 발급될 수 있다(토큰 재사용 공격 가능).

추가로 `readOnly = true`는 Redis 쓰기 연산(`delete`, `save`)이 포함된 메서드에 의미적으로 부정확하다.

**수정 방향:**
```java
// Redis Lua 스크립트로 atomic check-and-delete 구현
private static final String ROTATE_SCRIPT =
    "local v = redis.call('GET', KEYS[1]) " +
    "if v then redis.call('DEL', KEYS[1]) return v else return nil end";

// 또는 Spring Data Redis RedisScript 활용
// @Transactional(readOnly = true) → @Transactional 로 변경 (쓰기 의도 명시)
@Transactional
public RefreshResponse refresh(RefreshRequest request) {
    String key = "refresh:" + request.getRefreshToken();
    // atomicGetAndDelete(key) → empty면 즉시 예외
    ...
}
```

---

### CR-03 · JWKS 공개키 런타임 갱신 불가 — auth-service 재시작 시 Gateway 영구 401

**파일:** `gateway/.../client/JwksClient.java:30-55`

**문제:**  
공개키를 `@PostConstruct`에서 1회만 fetch하고 `volatile` 없이 단순 필드에 저장한다. CR-01과 결합하면 auth-service 재시작마다 키가 바뀌지만 Gateway는 이를 감지하지 못해 모든 요청이 영구 401 상태가 된다. 재시작이 유일한 복구 수단이다.

또한 `WebClient.create()`를 매 시도마다 생성한다(불필요한 객체 생성이며 WebClient는 재사용 설계).

**수정 방향:**
```java
// 주기적 갱신 + volatile 보장
private volatile RSAPublicKey publicKey;

@Scheduled(fixedDelayString = "${auth.jwks-refresh-interval-ms:300000}")
public void refreshPublicKey() { ... }

// WebClient는 Bean으로 주입받아 재사용
private final WebClient webClient;
```

---

## HIGH

### HR-01 · 비밀번호 평문 로그 노출 가능성 — `@Transactional(readOnly = true)` 상태로 login() 호출

**파일:** `auth-service/.../service/AuthService.java:44`

**문제:**  
`login()`에 `@Transactional(readOnly = true)`가 선언되어 있다. 이 자체는 오류는 아니나, `refreshTokenRepository.save()`(Redis 쓰기)를 트랜잭션 외부에서 호출하면서 쓰기 의도와 다른 어노테이션이 붙어 있어 코드 독해 혼란을 유발한다. `readOnly = true` 제거 필요.

또한 `issueRefreshToken()`이 `UUID.randomUUID()`를 사용하는데 이는 java.util.UUID로 암호학적으로 안전한 난수(SecureRandom)가 아니다. JDK UUID는 내부적으로 `SecureRandom`을 사용하므로 실제로는 안전하지만, 명시성을 위해 코드에 의도를 드러내는 것이 좋다.

**수정 방향:**
```java
// login() 의 readOnly=true 제거
@Transactional
public LoginResponse login(LoginRequest request) { ... }
```

---

### HR-02 · `claims.getExpirationTime()` NullPointerException

**파일:** `gateway/.../filter/JwtAuthenticationFilter.java:72`

**문제:**  
`claims.getExpirationTime()`은 JWT에 `exp` 클레임이 없으면 `null`을 반환한다. `.before(new Date())`를 바로 호출하면 NPE가 발생하고 catch 블록에서 `onUnauthorized`로 처리되기는 하지만, NPE를 명시적으로 처리하지 않아 의도가 불명확하다. auth-service의 `JwtProvider.validateAccessToken:71`도 동일 패턴이다.

**수정 방향:**
```java
Date expiry = claims.getExpirationTime();
if (expiry == null || expiry.before(new Date())) {
    log.warn("JWT 만료 또는 exp 클레임 없음: path={}", path);
    return onUnauthorized(exchange);
}
```

---

### HR-03 · Whitelist 경로 prefix 매칭 오염 — `/api/v1/auth/.well-known` 누락

**파일:** `gateway/.../filter/JwtAuthenticationFilter.java:30-37`

**문제:**  
`WHITE_LIST`에 `/api/v1/auth/.well-known`이 있지만 실제 JWKS 엔드포인트 경로는 `/api/v1/auth/.well-known/jwks.json`이다. `startsWith` 방식이므로 이 경우는 동작하지만, `/actuator`는 `/actuatorXXX` 같은 경로도 통과시킨다. prefix 매칭의 부작용이 있으며 actuator 엔드포인트 보호가 실질적으로 무력화될 수 있다.

또한 `WHITE_LIST`에 `/api/v1/auth/logout`이 포함되어 있어 **인증 없이 로그아웃** 요청이 통과된다. 공격자가 타인의 refresh token을 획득한 경우 인증 없이 삭제 가능하다(이중 날검).

**수정 방향:**
```java
// 정확한 경로 매칭 + prefix를 명확히 구분
private static final List<String> EXACT_WHITE_LIST = List.of(
    "/api/v1/auth/login",
    "/api/v1/auth/signup",
    "/api/v1/auth/refresh",
    "/api/v1/auth/.well-known/jwks.json"
);
// /actuator는 별도 관리 또는 Gateway 라우트 자체를 제거
```

---

### HR-04 · 민감 헤더 하위 서비스 전달 — X-User-Id 위조 가능

**파일:** `gateway/.../filter/JwtAuthenticationFilter.java:80-83`

**문제:**  
클라이언트가 임의의 `X-User-Id`, `X-User-Role` 헤더를 요청에 포함하면 `.mutate().header()`가 기존 값에 **추가(append)**한다. `header()` 메서드는 헤더를 replace하지 않고 add하므로, 하위 서비스가 첫 번째 값을 사용하는 경우 클라이언트가 제공한 위조값이 전달될 수 있다.

**수정 방향:**
```java
ServerHttpRequest mutated = exchange.getRequest().mutate()
        .headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Role");
        })
        .header("X-User-Id",   userId)
        .header("X-User-Role", role)
        .build();
```

---

### HR-05 · `JwksClient.loadPublicKey()` — `Thread.sleep()` in `@PostConstruct` (블로킹 WebFlux 스레드)

**파일:** `gateway/.../client/JwksClient.java:48`

**문제:**  
Gateway는 WebFlux(Netty) 기반이다. `@PostConstruct`는 서버 초기화 스레드에서 실행되므로 직접적인 이벤트 루프 블로킹은 아니지만, `WebClient.block()`과 `Thread.sleep()`을 혼합한 동기 블로킹 패턴은 WebFlux 관례에 어긋나며 서버 기동 지연(최대 3회×2초=6초) 및 스레드 점유 문제를 유발한다.

**수정 방향:**  
`@PostConstruct` 대신 `ApplicationRunner` + 비동기 retry(`retryWhen(Retry.fixedDelay(...))`) 활용.

---

## MEDIUM

### MD-01 · `AuthController`가 `JwtProvider`를 직접 의존 — 레이어 책임 위반

**파일:** `auth-service/.../controller/AuthController.java:33,61-66`

**문제:**  
Controller는 요청/응답 처리만 담당해야 한다. `jwks()` 엔드포인트에서 `JwtProvider.getPublicKey()`를 호출해 `JWKSet`을 직접 조립하는 것은 비즈니스 로직에 해당한다. 프로젝트 코딩 규칙 위반.

**수정 방향:**
```java
// AuthService에 위임
public Map<String, Object> getJwks() {
    RSAKey rsaKey = new RSAKey.Builder(jwtProvider.getPublicKey())
            .keyID(KEY_ID).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build();
    return new JWKSet(rsaKey).toJSONObject();
}
// Controller
@GetMapping("/.well-known/jwks.json")
public ResponseEntity<Map<String, Object>> jwks() {
    return ResponseEntity.ok(authService.getJwks());
}
```

---

### MD-02 · 매직 문자열 `"auth-key"` (keyID)

**파일:** `auth-service/.../controller/AuthController.java:63`

**문제:**  
`"auth-key"` 가 하드코딩되어 있다. 상수로 선언해야 한다. 프로젝트 품질 규칙 위반.

**수정 방향:**
```java
private static final String JWT_KEY_ID = "auth-key";
```

---

### MD-03 · `application.yml` 기본값에 자격증명 평문 노출

**파일:** `auth-service/src/main/resources/application.yml:6-7`

**문제:**  
`${DB_USERNAME:eoghks}`, `${DB_PASSWORD:eoghks_local}` — 환경변수 미설정 시 기본값으로 평문 자격증명이 사용된다. 이 파일이 형상관리에 포함되면 자격증명이 레포지토리에 노출된다.

**수정 방향:**
```yaml
username: ${DB_USERNAME}   # 기본값 제거 — 필수 환경변수로 강제
password: ${DB_PASSWORD}
```

---

### MD-04 · `JwksClient.getPublicKey()` — thread-safety 미보장

**파일:** `gateway/.../client/JwksClient.java:27,57`

**문제:**  
`privateKey publicKey` 필드가 `volatile`이 아니다. 단일 기동 시에는 문제 없지만 CR-03에서 권고한 주기적 갱신을 구현할 경우 가시성 문제가 발생한다. 선제적으로 `volatile` 선언이 필요하다.

**수정 방향:**
```java
private volatile RSAPublicKey publicKey;
```

---

### MD-05 · `JwtProvider.issue()` — 미사용 하위호환 메서드

**파일:** `auth-service/.../jwt/JwtProvider.java:94-96`

**문제:**  
`issue(Long, String)` 메서드가 `issueAccessToken()`을 위임 호출하는 형태로 존재한다. 주석은 "하위 호환 유지"이지만 내부 코드베이스에 실제 호출 지점이 없는 경우 데드코드다. 제거하거나 `@Deprecated`를 명시해야 한다.

---

### MD-06 · `CorsConfig` — 운영 Origin 하드코딩

**파일:** `gateway/.../config/CorsConfig.java:21`

**문제:**  
`"http://localhost:3000"` 이 하드코딩되어 있다. 주석에도 "운영: 허용 Origin을 환경변수로 분리할 것"이라고 명시되어 있으나 구현되지 않았다.

**수정 방향:**
```java
@Value("${cors.allowed-origins:http://localhost:3000}")
private List<String> allowedOrigins;
```

---

## LOW

### LW-01 · `AuthService.refresh()` — `@Transactional(readOnly = true)` 의미 부정확

**파일:** `auth-service/.../service/AuthService.java:60`

**문제:**  
`refresh()` 내부에서 `refreshTokenRepository.delete()`, `refreshTokenRepository.save()`(Redis 쓰기)를 수행하면서 `readOnly = true`로 선언되어 있다. 쓰기 의도와 어노테이션이 불일치해 가독성을 해친다.

---

### LW-02 · `SignupResponse` — Entity 직접 수신

**파일:** `auth-service/.../dto/SignupResponse.java:13`

**문제:**  
생성자가 `User` 엔티티를 직접 받아 DTO를 생성한다. DTO가 도메인 엔티티에 강결합된다. 향후 엔티티 변경 시 DTO 생성자도 같이 수정해야 한다. 정적 팩토리 메서드나 `@Builder`로 분리하는 것이 낫다.

---

### LW-03 · `JwtProvider.validateAccessToken()` — 내부 미사용 가능성 확인 필요

**파일:** `auth-service/.../jwt/JwtProvider.java:64`

**문제:**  
`validateAccessToken()`이 auth-service 내부에서 호출되는 지점이 코드상 보이지 않는다. Gateway에서 검증을 수행하므로 auth-service에 이 메서드가 존재하는 이유가 불명확하다. 향후 토큰 검증 API(`/api/v1/auth/validate`) 추가 시 사용 예정이라면 주석으로 의도를 명시할 것.

---

### LW-04 · `management.endpoint.health.show-details: always` — 운영 정보 노출

**파일:** `gateway/src/main/resources/application.yml:33-35`

**문제:**  
`show-details: always`로 설정하면 actuator health 엔드포인트에서 DB 연결 상태, Redis 연결 정보 등 내부 인프라 정보가 노출된다. 운영 환경에서는 `when-authorized` 또는 `never`로 변경해야 한다.

---

## 레이어 책임 위반 요약

| 위반 위치 | 내용 | 심각도 |
|-----------|------|--------|
| `AuthController.jwks()` | `JwtProvider` 직접 의존 + JWK 조립 로직 수행 | MEDIUM (MD-01) |

---

## 보안 취약점 요약

| ID | 내용 | 심각도 |
|----|------|--------|
| CR-01 | RSA 키 휘발 → 재시작 시 전체 토큰 무효화 | CRITICAL |
| CR-02 | Refresh Token Rotation TOCTOU | CRITICAL |
| CR-03 | JWKS 공개키 런타임 갱신 불가 | CRITICAL |
| HR-03 | Whitelist prefix 오염 + logout 미인증 통과 | HIGH |
| HR-04 | X-User-Id/Role 헤더 위조 가능 | HIGH |
| MD-03 | DB 자격증명 application.yml 평문 노출 | MEDIUM |

---

_Reviewed: 2026-05-07_  
_Reviewer: Claude (gsd-code-reviewer) — standard depth_
