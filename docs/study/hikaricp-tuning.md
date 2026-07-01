# HikariCP 튜닝

> 면접 대비 정리 문서

---

## 커넥션 풀이란

애플리케이션 시작 시 DB 커넥션을 미리 N개 만들어두고 요청마다 빌려쓰고 반납하는 구조.

매 요청마다 커넥션을 새로 맺으면 TCP 핸드쉐이크 + DB 인증 비용이 크기 때문에 미리 만들어두는 것.

```
요청 → 풀에서 커넥션 대출 → 쿼리 실행 → 커넥션 반납 → 풀로 복귀
```

---

## maximumPoolSize를 무조건 크게 잡으면 안 되는 이유

```
스레드 100개 + 커넥션 100개
→ 100개가 동시에 DB 쿼리 실행 시도
→ CPU 코어가 8개면 92개는 컨텍스트 스위칭 대기
→ 스위칭 비용이 오히려 성능 저하
```

DB 서버 CPU도 동시에 처리할 수 있는 양이 정해져 있어서 커넥션이 많다고 빨라지지 않음.

---

## Hikari 권장 공식

```
maximumPoolSize = (코어 수 * 2) + 유효 디스크 수
```

- 코어 8개, SSD 1개 → `8 * 2 + 1 = 17`
- 실무에서는 보통 **10~20** 사이로 잡고 부하 테스트로 튜닝

---

## Spring Boot 기본 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10       # 최대 커넥션 수 (기본값)
      minimum-idle: 10            # 최소 유지 커넥션 수
      connection-timeout: 30000   # 커넥션 대기 최대 시간 (ms)
      idle-timeout: 600000        # 유휴 커넥션 제거 시간 (ms)
      max-lifetime: 1800000       # 커넥션 최대 수명 (ms)
```

`connection-timeout` 초과 시 `SQLTransientConnectionException` 발생.

---

## 커넥션 풀 고갈 시나리오

```
maximumPoolSize = 10
동시 요청 = 50

→ 10개 즉시 처리
→ 40개 대기 (connection-timeout 30초)
→ 30초 내 반납 안 되면 예외 발생
```

---

## 면접 예상 질문

**Q. 커넥션 풀 사이즈를 크게 잡으면 왜 안 되나요?**
> DB 서버 CPU 코어 수 이상의 커넥션은 컨텍스트 스위칭 비용만 늘어납니다. 커넥션이 많다고 처리량이 늘지 않고 오히려 줄 수 있습니다.

**Q. HikariCP 사이즈는 어떻게 결정하나요?**
> `코어 수 * 2 + 디스크 수` 공식을 기준으로 잡고, 실제 부하 테스트로 최적값을 찾습니다.

**Q. 커넥션 풀이 고갈되면 어떻게 되나요?**
> `connection-timeout` 동안 대기하다가 초과하면 `SQLTransientConnectionException`이 발생합니다. 이를 방지하려면 Redis 락처럼 DB 커넥션을 짧게 점유하는 설계가 중요합니다.
