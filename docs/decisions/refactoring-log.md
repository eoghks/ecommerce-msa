# Refactoring Log

코드 리뷰 및 품질 개선 이력. 날짜별로 누적 기록.

---

## 2026-05-08

### 대상 모듈: `auth-service` · `gateway`

| # | 항목 | 원인 | 수정 내용 |
|---|------|------|----------|
| 1 | JWT exp null 체크 누락 | `getExpirationTime()` 반환값이 null일 때 NPE 발생 가능 | Gateway 필터 + JwtProvider 양쪽에 null 체크 추가 |
| 2 | X-User-Id/Role 헤더 위조 가능 | `header()` 메서드가 기존 헤더에 append 방식으로 동작 | 하위 서비스 전달 전 `h.remove()` 후 재설정 |
| 3 | `@Transactional(readOnly=true)` 오용 | `login()` / `refresh()` 내부에서 Redis 쓰기 연산 수행 | `readOnly` 제거 → `@Transactional` 단순화 |
| 4 | Controller 레이어 책임 위반 | `AuthController`가 `JwtProvider`를 직접 주입하여 JWK 조립 | JWKS 조립 로직 `AuthService.getJwks()`로 이동 |
| 5 | 매직 문자열 `"auth-key"` | keyID 값이 하드코딩되어 변경 시 누락 위험 | `JWT_KEY_ID` 상수로 추출 (`AuthService`) |
| 6 | `publicKey` 필드 thread-safety 미보장 | `@Scheduled` 갱신 시 가시성 문제 발생 가능 | `volatile` 선언 |
| 7 | `JwtProvider.issue()` 미사용 메서드 | 내부 호출 지점 없는 하위호환 메서드 잔존 | `@Deprecated(forRemoval=true)` 표시 |
| 8 | CORS origin 하드코딩 | `"http://localhost:3000"` 코드에 고정 | `@Value("${cors.allowed-origins}")` 환경변수화 |
| 9 | JWKS 공개키 런타임 갱신 불가 | `@PostConstruct` 1회 fetch 후 영구 캐싱 → auth-service 재시작 시 Gateway 영구 401 | `@Scheduled` 5분 주기 갱신 + `volatile` 적용 |
| 10 | actuator 내부 정보 노출 | `show-details: always` → DB·Redis 연결 정보 외부 노출 | `show-details: when-authorized` 변경 |

---

<!-- 다음 리팩토링 이력은 아래에 날짜별로 추가 -->
