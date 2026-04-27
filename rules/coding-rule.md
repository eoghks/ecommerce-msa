# 코딩 규칙

## 공통
- Java 21, `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용
- 한국어 주석 허용
- 메서드 30줄 권장 (강제 아님 — 검증·매핑 등 자연스럽게 길어지는 경우 허용)
- null 대신 `Optional` (반환 타입에만, 필드·파라미터 금지) 또는 빈 컬렉션
- 매직 넘버/문자열은 상수 또는 `enum`
- 함수 인자로 `Map` 금지 — 명시적 DTO 사용
- 가능한 곳은 `final` 적용 (불변성)

## 도메인 타입 (이커머스)
- **금액은 `BigDecimal`** — `double`/`float` 금지
- 시간은 `Instant` 기본, 사용자 표시용 `LocalDateTime` (타임존 명확히)
- 통화는 `Currency` 또는 ISO 4217 문자열

## 의존성 주입
- 생성자 주입만 (`@RequiredArgsConstructor` + `private final`)
- 필드/세터 주입 금지

## DTO
- Request/Response DTO 는 기본 `record` (불변)
- 상속·커스텀 직렬화 등 필요 시 class 허용
- Entity 는 `class`
- Bean Validation은 Request DTO 에 명시

## RESTful 설계
- HTTP Method: `GET`/`POST`/`PUT`/`PATCH`/`DELETE`
- URI: 리소스 명사, 복수형, kebab-case ([naming-convention.md](naming-convention.md))
- API 버저닝: `/api/v1/...`
- HTTP Status Code:
  - `200`/`201`(+Location)/`204`
  - `400`/`401`/`403`/`404`/`409`/`422`/`500`

## 페이징·정렬
- `Pageable` 사용
- 응답: `content`, `page`, `size`, `totalElements`, `totalPages`
- 최대 `size` 100 제한
- 정렬: `?sort=createdAt,desc`

## 멱등성
- 결제·주문 등은 `Idempotency-Key` 헤더 필수
- 동일 키 24시간 재호출 → 이전 결과 반환 (Redis)

## 레이어 책임
- **Controller**: 입력 바인딩 + `@Valid` 만, `ResponseEntity<DTO>` 직접 반환
- **Service**: 비즈니스 로직, `@Transactional` (조회는 `readOnly`)
- **Repository**: 단순 메서드 네이밍, 복잡 쿼리는 QueryDSL
  - 통계·배치 등 비효율 케이스는 JPQL/네이티브 허용 (사유 주석)

## JPA
- 연관관계 `FetchType.LAZY` 기본
- 양방향 연관관계 최소화
- N+1 방지: `fetch join` / `@EntityGraph` / `default_batch_fetch_size`
- Entity `@Setter` 금지 — 도메인 메서드로 상태 변경
- `@EqualsAndHashCode(of = "id")` 만
- Entity 기본 생성자: `@NoArgsConstructor(access = PROTECTED)`
- 주문 시점 상품 가격 등은 **스냅샷 저장**

## DB 마이그레이션
- **Flyway** 사용 (`db/migration/V{version}__{desc}.sql`)
- `spring.jpa.hibernate.ddl-auto=validate` (운영 시뮬레이션), `none` (배포)
- `create`/`update` 운영 사용 금지
- 마이그레이션은 forward-only — rollback 은 신규 forward 마이그레이션으로

## 트랜잭션
- 같은 클래스 내 메서드 자기 호출 시 `@Transactional` 미적용 — 분리 필요
- 외부 API 호출은 트랜잭션 경계 밖
- 조회 메서드는 `@Transactional(readOnly = true)`

## 예외 처리
- 실패는 커스텀 예외 throw (Controller try/catch 금지)
- 전역 처리: `@RestControllerAdvice` + `ProblemDetail` (RFC 7807)
- 예외 메시지 추상화, 스택트레이스 응답 노출 금지

## 로깅
- `@Slf4j` 사용, `System.out.println` 금지
- Level: `ERROR`/`WARN`/`INFO`/`DEBUG`
- 민감정보 출력 금지 (마스킹)
- 변수는 `{}` 플레이스홀더
- MDC 키: `requestId`, `correlationId` ([naming-convention.md](naming-convention.md))

## 관측성
- Micrometer + Prometheus 메트릭 노출
- 분산 추적: OpenTelemetry [운영]
- `X-Request-ID` 자동 부여 → MDC

## MSA 서비스 간 통신
- `OpenFeign` (`RestTemplate` 지양)
- Resilience4j: 타임아웃 / 재시도 / 서킷브레이커
- 서비스 간 호출도 인증 필수 — [security-rule.md](security-rule.md)

## 분산 트랜잭션
- 서비스 경계는 **Saga (Choreography, 이벤트 기반)** + **Apache Kafka**
- 이벤트 발행: **Outbox 패턴** (DB 트랜잭션 원자성)
- 이벤트 소비: 멱등 처리 (`eventId` 중복 방지)
- `correlationId` 분산 추적
- 컨슈머 그룹 / DLT 네이밍 → [naming-convention.md](naming-convention.md)
- 상세: [docs/decisions/ADR-001-saga-pattern.md](../docs/decisions/ADR-001-saga-pattern.md)

## 테스트
- 단일 출처: [testing-rule.md](testing-rule.md)
- 핵심: 구현과 동시 작성, 커밋 전 통과 확인

## 파일 staging
- `.java` `.tsx` `.ts` `.xml` 생성 시 `git add <파일>` (개별)
- `git add .` / `git add -A` 금지 — WIP·민감 파일 사고 위험
- 커밋 전 `git status` 점검
