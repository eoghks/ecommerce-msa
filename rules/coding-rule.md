# 코딩 규칙

## 공통
- Java 21, `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용
- 한국어 주석 허용
- 메서드 30줄 이내
- null 대신 `Optional` / 빈 컬렉션
- 매직 넘버/문자열은 상수 또는 `enum`
- 함수 인자로 `Map` 금지 — 명시적 DTO 사용
- 가능한 곳은 `final` 적용 (불변성)

## 의존성 주입
- 생성자 주입만 사용 (`@RequiredArgsConstructor` + `private final`)
- 필드 주입(`@Autowired`) / 세터 주입 금지

## DTO
- Request / Response DTO는 `record` 로 작성 (불변)
- Entity 는 `class`
- Bean Validation(`@NotNull`, `@Email` 등)은 Request DTO 에 명시

## RESTful 설계
- HTTP Method: `GET`(조회) `POST`(생성) `PUT`(전체수정) `PATCH`(부분수정) `DELETE`(삭제)
- URI 는 리소스 명사 (동사 금지)
- HTTP Status Code 정확히:
  - `200` 조회/수정 / `201` 생성(+Location) / `204` 삭제
  - `400` 검증실패 / `401` 인증 / `403` 권한 / `404` 없음
  - `409` 중복·재고부족 / `422` 비즈니스규칙 위반 / `500` 서버오류

## 레이어 책임
- **Controller**: 입력 바인딩 + 검증(`@Valid`)만, 응답은 `ResponseEntity<DTO>` 직접 반환 (래퍼 금지)
- **Service**: 비즈니스 로직, `@Transactional` 관리 (조회는 `readOnly = true`)
- **Repository**: 단순 쿼리는 메서드 네이밍, 복잡 쿼리는 QueryDSL (JPQL·네이티브 지양)

## JPA 주의사항
- 연관관계 `FetchType.LAZY` 기본
- 양방향 연관관계 최소화 (필요할 때만)
- N+1 방지: `fetch join` / `@EntityGraph` / `default_batch_fetch_size`
- Entity 에 `@Setter` 금지 — 도메인 메서드로 상태 변경
- `@EqualsAndHashCode(of = "id")` 만 사용 (전체 필드 사용 금지)
- Entity 기본 생성자는 `@NoArgsConstructor(access = PROTECTED)`

## 트랜잭션
- 같은 클래스 내 메서드 자기 호출 시 `@Transactional` 미적용 — 분리 필요
- 외부 API 호출은 트랜잭션 경계 밖에서 수행
- 조회 메서드는 `@Transactional(readOnly = true)`

## 예외 처리
- 실패는 커스텀 예외 throw (Controller try/catch 금지)
- 전역 처리: `@RestControllerAdvice` + `ProblemDetail` (RFC 7807)
- 예외 메시지는 사용자에게 노출되지 않게 추상화

## 로깅
- `@Slf4j` 사용, `System.out.println` 금지
- Level: `ERROR`(예외) / `WARN`(비정상이지만 동작) / `INFO`(주요 흐름) / `DEBUG`(상세)
- 비밀번호·토큰·주민번호 등 민감정보 출력 금지 (마스킹 필수)
- 로그 메시지는 한국어 가능, 변수는 `{}` 플레이스홀더로 전달

## MSA 서비스 간 통신
- `OpenFeign` 사용 (`RestTemplate` 지양)
- Resilience4j 로 타임아웃 / 재시도 / 서킷브레이커 설정
- 모든 요청에 `X-Request-ID` 헤더 부여 → MDC 로 분산 로그 추적

## 분산 트랜잭션
- 서비스 경계를 넘는 트랜잭션은 **Saga 패턴 (Choreography, 이벤트 기반)** 사용
- 메시지 브로커: Apache Kafka
- 이벤트 발행은 **Outbox 패턴**으로 DB 트랜잭션과 원자성 보장
- 이벤트 소비는 멱등(idempotent) 처리 필수 (`eventId` 중복 방지)
- 모든 이벤트에 `correlationId` 포함 → 분산 추적
- 상세 근거: [docs/decisions/saga-pattern.md](../docs/decisions/saga-pattern.md)

## 파일 생성
- `.java` `.jsx` `.js` `.xml` 생성 즉시 `git add` 실행
