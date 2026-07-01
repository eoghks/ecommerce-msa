# CR-02 · Refresh Token Rotation TOCTOU 레이스 컨디션

**대상:** `auth-service` — `AuthService.refresh()`

## 현재 구현

```java
// 3단계 비원자적 연산
Long userId = refreshTokenRepository.findUserIdByToken(token); // (1) 조회
refreshTokenRepository.delete(token);                          // (2) 삭제
refreshTokenRepository.save(newToken, userId, ttl);            // (3) 저장
```

## 문제점

동일 Refresh Token으로 동시 요청이 오면 (1)을 둘 다 통과 → 새 토큰 2개 발급 (토큰 재사용 공격 가능)

## 운영 수준 해결책

**방법 1 — Redis Lua 스크립트 (원자적 GET+DEL)**
```lua
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

## 포트폴리오 선택 이유

동시 요청 시나리오가 극히 드문 로컬 단일 사용자 환경.
Lua 스크립트 구현 시 테스트 복잡도와 코드량이 크게 증가.

## 면접 답변 포인트

> "현재 구현에서 동시 Refresh 요청이 오면 TOCTOU 레이스 컨디션이 발생할 수 있습니다.
> 해결책으로 Redis Lua 스크립트로 GET+DEL을 원자적으로 처리하거나,
> Redisson 분산락을 사용할 수 있습니다. 트래픽이 많은 서비스에서는 필수 적용 사항입니다."
