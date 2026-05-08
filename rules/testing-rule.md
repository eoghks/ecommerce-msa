# 테스트 규칙

> **테스트 동시 작성** 원칙은 이 문서가 단일 출처(SoT). 다른 규칙 문서는 본 문서를 링크.

## 원칙
- 구현 코드와 테스트는 **동시 작성** (사후 작성 금지)
- 커밋 전 전체 통과 — 실패 시 수정 후 재실행
- 보고 형식: `통과 N건 / 실패 0건`
- **단위 테스트는 한 가지만 검증** — 통합/E2E 는 시나리오 단위 다중 assertion 허용
- AssertJ 사용 (`assertThat` 체이닝)
- Given-When-Then 주석으로 단계 명시

## 테스트 피라미드 (목표 비율)
| 레이어 | 비율 | 도구 | 분류 |
|--------|------|------|------|
| 단위 (Service) | 70% | JUnit 5 + Mockito | [토이 필수] |
| 슬라이스 (Repository, Controller) | 20% | `@DataJpaTest`, `@WebMvcTest` | [토이 필수] |
| 통합/E2E | 10% | `@SpringBootTest` + Testcontainers | [토이 필수] |
| 계약 (Contract) | 별도 | Spring Cloud Contract / Pact | [운영] |
| 성능 (Load) | 별도 | k6 / JMeter | [운영] |

## 외부 의존성 처리
- **단위 테스트**: Mockito Mock
- **Repository 테스트**:
  - 기본: `@DataJpaTest` + H2 (빠른 피드백)
  - PostgreSQL 전용 기능(JSONB 등) 사용 시: Testcontainers PostgreSQL
- **Kafka 테스트**:
  - 슬라이스 단위: `@EmbeddedKafka` (가벼움)
  - 통합 시나리오: Testcontainers Kafka
- **Redis**: Testcontainers Redis
- 통합 테스트는 **Reusable Testcontainers** (`.withReuse(true)`) — CI 시간 절감

## Saga / 이벤트 통합 테스트 [토이 필수]
- 정상 흐름: 주문 생성 → 재고 차감 → 결과 이벤트 검증
- 보상 흐름: 재고 부족 / 결제 실패 → 주문 취소 검증
- **부분 실패**: 타임아웃, 재시도 한계 초과 → DLT 이동 검증
- **중복 메시지**: 동일 `eventId` 2회 수신 시 결과 동일성 (멱등 검증)
- **이벤트 순서**: 동일 `correlationId` 의 처리 순서 보장
- **Outbox**: DB 트랜잭션 롤백 시 이벤트 미발행 검증
- 비동기 결과 대기는 `awaitility` (기본 5초 타임아웃, 폴링 200ms)

## 계약 테스트 [운영]
- API: Spring Cloud Contract 또는 Pact
- 이벤트: JSON Schema / Avro Schema Registry
- 토이 단계엔 미적용, 운영 환경에서 도입

## 성능 / 부하 테스트 [운영]
- 도구: k6 (또는 JMeter)
- 시나리오:
  - 주문 피크: 동일 상품 동시 100 주문 → 재고 정합성 + 응답 시간
  - 상품 목록 조회: 분당 1000 req → Redis 캐싱 효과
- 토이 단계엔 미적용, 운영 환경에서 도입

## 테스트 격리
- 통합 테스트는 트랜잭션 rollback 또는 `@Sql` 로 데이터 초기화
- `@DirtiesContext` 는 비용 큼 — 꼭 필요할 때만
- 테스트 간 상태 공유 금지
- Flaky 테스트는 root cause 분석 후 해결, 재시도로 덮지 않음

## 네이밍
- 클래스: `<대상>Test`
- 메서드: `<대상메서드>_<상황>_<기대결과>` (한국어 가능)
- `@DisplayName` 으로 한국어 시나리오 부연 권장

## 테스트 데이터
- **Test Data Builder 패턴** — `UserBuilder.aUser().withEmail(...).build()`
- `fixture/` 패키지에 도메인별 Builder 관리
- 메서드별 격리된 데이터 (글로벌 픽스처 지양)

## 커버리지
- **핵심 도메인 한정**: Saga, 결제, 재고 차감, 인증 — **브랜치 커버리지 80%**
- 전체 라인 커버리지 60%
- JaCoCo 리포트 + CI 임계 게이트 (미달 시 PR 차단)

## 실행 분리 (Gradle)
- `test` — 단위·슬라이스 (빠름, 로컬 기본)
- `integrationTest` — Testcontainers 통합 (느림, CI)
- `contractTest` — 계약 테스트 [운영]
- `performanceTest` — k6 [운영]

## 차별화 도구 (선택)
- **ArchUnit** — 레이어 의존성 / 네이밍 규칙 자동 검증
- **Mutation Testing (PIT)** — 테스트 품질 검증
- **SonarQube** Quality Gate — CI 게이팅

## 테스트 결과 문서화 (`docs/test/`)
- 테스트 진행 시 반드시 결과 문서 작성: `docs/test/YYYY-MM-DD-{slug}.md`
- 파일 형식: `테스트 환경 · 테스트 항목별 요청/응답/결과 · 미통과 항목 원인 분석 + 수정 내용 + 재테스트 결과`
- `docs/test/OVERVIEW.md`에 날짜·테스트 종류·대상 서비스·결과 요약 한 줄 추가
- 미통과 → 수정 → 재통과 흐름은 동일 문서 내에서 추적 (별도 파일 금지)

## 상세 전략
- [docs/decisions/ADR-006-testing-strategy.md](../docs/decisions/ADR-006-testing-strategy.md) (Week 5~6 작성)
