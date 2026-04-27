# 코딩 규칙

## 공통
- Java 21, `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용
- 한국어 주석 허용
- 메서드 30줄 권장 (강제 아님 — 검증·매핑 등 자연스럽게 길어지는 경우 허용)
- null 대신 `Optional` / 빈 컬렉션 (Optional 은 반환 타입에만, 필드·파라미터 금지)
- 매직 넘버/문자열은 상수 또는 `enum`
- 함수 인자로 `Map` 금지 — 명시적 DTO 사용
- 가능한 곳은 `final` 적용 (불변성)

## 도메인 타입 규칙 (이커머스)
- **금액은 `BigDecimal`** — `double`/`float` 금지 (부동소수점 오차)
- 시간은 `Instant` 기본, 사용자 표시용 `LocalDateTime` (타임존 명확히)
- 통화는 `Currency` 또는 ISO 4217 문자열 (`KRW`, `USD`)

## 의존성 주입
- 생성자 주입만 사용 (`@RequiredArgsConstructor` + `private final`)
- 필드 주입(`@Autowired`) / 세터 주입 금지

## DTO
- Request / Response DTO는 기본 `record` (불변)
- 단, 상속·커스텀 직렬화·`@JsonInclude` 등 필요 시 class 허용
- Entity 는 `class`
- Bean Validation(`@NotNull`, `@Email` 등)은 Request DTO 에 명시

## RESTful 설계
- HTTP Method: `GET`(조회) `POST`(생성) `PUT`(전체수정) `PATCH`(부분수정) `DELETE`(삭제)
  - 실무에서 PATCH 위주, PUT 은 진짜 전체 교체일 때만
- URI 는 리소스 명사 (동사 금지)
- **API 버저닝**: `/api/v{version}/...` (`/api/v1/products`)
- HTTP Status Code 정확히:
  - `200` 조회/수정 / `201` 생성(+Location) / `204` 삭제
  - `400` 검증실패 / `401` 인증 / `403` 권한 / `404` 없음
  - `409` 중복·재고부족 / `422` 비즈니스규칙 위반 / `500` 서버오류

## 페이징·정렬
- 목록 조회는 `Pageable` 사용
- 응답 포맷: `content`, `page`, `size`, `totalElements`, `totalPages`
- 최대 `size` 제한 (예: 100) — 무한 조회 차단
- 정렬: `?sort=createdAt,desc`

## 멱등성
- 결제·주문 등 중복 호출 위험 API 는 `Idempotency-Key` 헤더 필수
- 동일 키 24시간 내 재호출 → 이전 결과 반환 (Redis 저장)

## 레이어 책임
- **Controller**: 입력 바인딩 + 검증(`@Valid`)만, 응답은 `ResponseEntity<DTO>` 직접 반환
- **Service**: 비즈니스 로직, `@Transactional` 관리 (조회는 `readOnly = true`)
- **Repository**: 단순 쿼리는 메서드 네이밍, 복잡 쿼리는 QueryDSL
  - 통계·배치 등 QueryDSL 비효율 케이스는 JPQL/네이티브 허용 (사유 주석 필수)

## JPA 주의사항
- 연관관계 `FetchType.LAZY` 기본
- 양방향 연관관계 최소화
- N+1 방지: `fetch join` / `@EntityGraph` / `default_batch_fetch_size`
- Entity 에 `@Setter` 금지 — 도메인 메서드로 상태 변경
- `@EqualsAndHashCode(of = "id")` 만 사용
- Entity 기본 생성자: `@NoArgsConstructor(access = PROTECTED)`
- 주문 시점 상품 가격 등은 **스냅샷 저장** (Order 테이블에 가격 복사)

## DB 마이그레이션
- **Flyway** 사용 (`db/migration/V{version}__{description}.sql`)
- `spring.jpa.hibernate.ddl-auto=validate` (운영), `none` (배포)
- `create`, `update` 절대 운영에 사용 금지

## 트랜잭션
- 같은 클래스 내 메서드 자기 호출 시 `@Transactional` 미적용 — 분리 필요
- 외부 API 호출은 트랜잭션 경계 밖에서 수행
- 조회 메서드는 `@Transactional(readOnly = true)`

## 예외 처리
- 실패는 커스텀 예외 throw (Controller try/catch 금지)
- 전역 처리: `@RestControllerAdvice` + `ProblemDetail` (RFC 7807)
- 예외 메시지는 사용자에게 노출되지 않게 추상화
- 스택트레이스 응답 노출 금지

## 로깅
- `@Slf4j` 사용, `System.out.println` 금지
- Level: `ERROR`(예외) / `WARN`(비정상이지만 동작) / `INFO`(주요 흐름) / `DEBUG`(상세)
- 비밀번호·토큰·주민번호 등 민감정보 출력 금지 (마스킹)
- 변수는 `{}` 플레이스홀더로 전달

## 관측성
- Micrometer + Prometheus 메트릭 노출
- 분산 추적: OpenTelemetry (선택, 시간 여유 시)
- 모든 요청에 `X-Request-ID` → MDC 주입

## MSA 서비스 간 통신
- `OpenFeign` 사용 (`RestTemplate` 지양)
- Resilience4j: 타임아웃 / 재시도 / 서킷브레이커
- 모든 요청에 `X-Request-ID` 헤더 부여
- 서비스 간 호출도 인증 필수 (내부 JWT 또는 mTLS) — 상세는 [security-rule.md](security-rule.md)

## 분산 트랜잭션
- 서비스 경계를 넘는 트랜잭션은 **Saga 패턴 (Choreography, 이벤트 기반)** 사용
- 메시지 브로커: Apache Kafka
- 이벤트 발행은 **Outbox 패턴**으로 DB 트랜잭션과 원자성 보장
- 이벤트 소비는 멱등(idempotent) 처리 필수 (`eventId` 중복 방지)
- 모든 이벤트에 `correlationId` 포함 → 분산 추적
- 컨슈머 그룹 네이밍: `<service>-<topic>-consumer`
- DLQ(Dead Letter Queue) 운영 — 일정 횟수 실패 시 격리
- 상세 근거: [docs/decisions/saga-pattern.md](../docs/decisions/saga-pattern.md)

## 테스트
- 구현 코드와 테스트 코드는 **동시 작성**
- 커밋 전 전체 테스트 통과 확인
- 상세 규칙: [testing-rule.md](testing-rule.md)

## 파일 생성 / 스테이징
- `.java` `.jsx` `.js` `.xml` 생성 시 `git add <파일>` (개별 파일 명시)
- `git add .` / `git add -A` 금지 — WIP·민감 파일 실수 staging 위험
- 커밋 전 `git status` 로 staged 영역 점검
