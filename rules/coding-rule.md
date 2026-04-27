# 코딩 규칙

## 공통
- Java 21 사용 (`var` 키워드 적극 활용)
- `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용: `@Getter` `@Setter` `@Builder` `@Slf4j` `@RequiredArgsConstructor`
- 한국어 주석 허용
- 메서드는 30줄 이내 유지 (초과 시 분리)
- null 반환 대신 `Optional` 또는 빈 컬렉션 반환
- 매직 넘버/문자열은 상수로 선언
- 함수 인자로 `Map` 대신 명시적 DTO/파라미터 사용

## 레이어 책임

### Controller
- 요청/응답 처리, 입력값 바인딩만
- 비즈니스 로직 금지
- 응답은 반드시 `ResponseEntity<ApiResponse<?>>` 사용
- 입력값 검증: `@Valid` + DTO에 Bean Validation 적용

### Service
- 비즈니스 로직 전담
- 트랜잭션 경계 관리 (`@Transactional`)
- 조회 메서드는 `@Transactional(readOnly = true)` 적용

### Repository
- DB 접근만 — Spring Data JPA Repository 호출만 허용
- 복잡한 쿼리는 JPQL 또는 QueryDSL 사용
- 네이티브 쿼리 최소화

## 예외 처리
- 체크 예외는 의미 있는 메시지와 함께 커스텀 예외로 래핑
- 전역 예외는 `@RestControllerAdvice` 에서 처리
- 예외 메시지는 사용자에게 노출되지 않도록 추상화

## 파일 생성 규칙
- `.java` `.jsx` `.js` `.xml` 파일 생성 후 즉시 `git add <파일경로>` 실행
