# Known Tradeoffs & 기술 부채

포트폴리오 범위 또는 인프라 제약으로 현재 구현에서 의도적으로 선택한 트레이드오프.
면접 질문 대비 및 향후 개선 참고용으로 작성.

---

## CR-01 · RSA 키 페어 인메모리 생성

### 현재 구현
```java
// JwtProvider.java
@PostConstruct
public void init() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();  // 매 기동 시 새 키 생성
    ...
}
```

### 문제점
- 서비스 **재시작 시** 기존 발급된 모든 Access Token 즉시 무효화
- **스케일 아웃** 시 인스턴스마다 다른 키 → 다른 인스턴스가 발급한 토큰 검증 불가

### 운영 수준 해결책
```yaml
# application.yml
jwt:
  private-key-pem: ${JWT_PRIVATE_KEY}   # K8s Secret 또는 Vault 주입
  public-key-pem:  ${JWT_PUBLIC_KEY}
```
```java
@PostConstruct
public void init() throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(
        privateKeyPem.replaceAll("-----.*-----|\n", ""));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    // publicKey 동일
}
```

### 포트폴리오 선택 이유
단일 인스턴스 로컬 환경 → 재시작 빈도 낮고 스케일 아웃 없음.
키 관리 인프라(Vault, K8s Secret) 없이 PEM 주입 구현 시 `.env` 파일이나 `application.yml`에
평문 키가 노출되어 보안상 더 나쁠 수 있음.

### 면접 답변 포인트
> "현재는 인메모리 생성 방식인데, 운영 환경에서는 AWS Secrets Manager나 Kubernetes Secret으로
> PEM을 외부 주입해야 합니다. 특히 스케일 아웃 환경에서는 모든 인스턴스가 동일한 키를 공유해야
> Gateway가 어떤 인스턴스가 발급한 토큰도 검증할 수 있습니다."

---

## CR-02 · Refresh Token Rotation TOCTOU 레이스 컨디션

### 현재 구현
```java
// AuthService.java — 3단계 비원자적 연산
Long userId = refreshTokenRepository.findUserIdByToken(token); // (1) 조회
refreshTokenRepository.delete(token);                          // (2) 삭제
refreshTokenRepository.save(newToken, userId, ttl);            // (3) 저장
```

### 문제점
동일 Refresh Token으로 동시 요청이 오면 (1)을 둘 다 통과 → 새 토큰 2개 발급 (토큰 재사용 공격 가능)

### 운영 수준 해결책

**방법 1 — Redis Lua 스크립트 (원자적 GET+DEL)**
```lua
-- rotate.lua
local val = redis.call('GET', KEYS[1])
if val then
  redis.call('DEL', KEYS[1])
  return val
else
  return nil
end
```
```java
RedisScript<String> script = RedisScript.of(luaScript, String.class);
String userId = redisTemplate.execute(script, List.of(key));
if (userId == null) throw new IllegalArgumentException("이미 사용된 토큰");
```

**방법 2 — Redis SET NX (분산락)**
```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent("lock:refresh:" + token, "1", Duration.ofSeconds(5));
if (!acquired) throw new IllegalArgumentException("동시 요청 감지");
```

### 포트폴리오 선택 이유
동시 요청 시나리오가 극히 드문 로컬 단일 사용자 환경.
Lua 스크립트 구현 시 테스트 복잡도와 코드량이 크게 증가.

### 면접 답변 포인트
> "현재 구현에서 동시 Refresh 요청이 오면 TOCTOU 레이스 컨디션이 발생할 수 있습니다.
> 해결책으로 Redis Lua 스크립트로 GET+DEL을 원자적으로 처리하거나,
> Redisson 분산락을 사용할 수 있습니다. 트래픽이 많은 서비스에서는 필수 적용 사항입니다."

---

## MD-03 · DB 자격증명 application.yml 기본값 노출

### 현재 구현
```yaml
# auth-service/application.yml
datasource:
  username: ${DB_USERNAME:eoghks}          # 기본값에 실제 계정명
  password: ${DB_PASSWORD:eoghks_local}    # 기본값에 실제 비밀번호
```

### 문제점
환경변수 미설정 시 기본값으로 평문 자격증명 사용.
형상관리(GitHub)에 포함되면 자격증명 노출.

### 운영 수준 해결책
```yaml
# 기본값 제거 — 환경변수 필수화
datasource:
  username: ${DB_USERNAME}
  password: ${DB_PASSWORD}
```
```bash
# .env (gitignore에 포함)
DB_USERNAME=eoghks
DB_PASSWORD=eoghks_local
```
또는 Docker Compose `env_file`, K8s Secret, AWS Parameter Store 사용.

### 포트폴리오 선택 이유
로컬 개발 편의성. 현재 계정(`eoghks`)은 로컬 전용 계정이라 실제 보안 위협 없음.
`.env` 파일 추가 시 Docker Compose와 Spring Boot 모두 설정 변경 필요해 복잡도 증가.

### 면접 답변 포인트
> "로컬 개발 환경 편의를 위해 기본값을 설정했습니다. 운영 배포 시에는 기본값을 제거하고
> 환경변수를 필수화하거나, AWS Secrets Manager나 Spring Cloud Config를 통해
> 자격증명을 외부 관리합니다. 또한 비밀번호는 bcrypt 등으로 단방향 해싱된 값을 DB에 저장하므로
> 평문 저장은 없습니다."
