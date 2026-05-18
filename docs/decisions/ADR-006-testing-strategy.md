# ADR-006: 테스트 전략

## Status
✅ 확정 — Week 3 (product-service 테스트 정비 완료 기준)

## Date
2026-05-18

---

> 테스트 작성 기준·도구·커버리지 등 상세 규칙은 [`rules/testing-rule.md`](../../rules/testing-rule.md) 가 단일 출처(SoT). 이 ADR 은 **선택의 근거**만 기록.

## Context
- 1인 토이 프로젝트 + MSA 환경에서 어떤 테스트 전략으로 가야 하는가
- 단위/슬라이스/통합/계약/성능 중 어디에 시간 투자할지
- Docker Desktop 29.x 환경, Windows, Testcontainers 사용

## Decision

**테스트 피라미드** — 레이어별 책임에 맞는 도구를 선택한다.

| 레이어 | 도구 | 비율 | 목적 |
|--------|------|------|------|
| Domain (엔티티, VO) | JUnit5 순수 단위 | ~50% | 비즈니스 룰 검증, 빠른 피드백 |
| Service (비즈니스 로직) | JUnit5 + Mockito | ~30% | 의존성 격리, 시나리오별 분기 검증 |
| Repository (쿼리) | `@DataJpaTest` + Testcontainers PostgreSQL | ~15% | 실제 DB 방언 기반 쿼리 검증 |
| E2E / 통합 | `@SpringBootTest` + Testcontainers | ~5% | 전체 HTTP 흐름 검증 |

## Considered Options

- **A. 단위 테스트 비중 극대화 (90%+)**: 빠른 피드백, 적은 통합 신뢰 — H2를 쓰면 실제 PostgreSQL 방언과 차이가 생김
- **B. 테스트 피라미드 (채택)**: 레이어별 책임 분리, 현실적인 실행 속도와 신뢰성 균형
- **C. 통합 테스트 비중 확대**: 신뢰성↑, 속도↓, 토이 프로젝트 규모에서 유지 비용 과다

## 핵심 선택 근거

### Repository — H2 대신 Testcontainers PostgreSQL
- H2의 호환 모드는 PostgreSQL 방언 일부를 에뮬레이션하지만 JPQL/QueryDSL의 `ILIKE`, 함수, 타입 캐스팅 동작이 다름
- Testcontainers로 실제 `postgres:16-alpine` 컨테이너를 띄워 운영 환경과 동일한 DB에서 쿼리 검증
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@ServiceConnection` 조합으로 슬라이스 테스트 유지

### Service — Mockito 격리
- Redis, Repository 등 외부 의존성을 Mock으로 대체하여 비즈니스 로직만 집중 검증
- Redis 직렬화 변경(`RedisTemplate<String,String>` + `ObjectMapper`) 이후 Mock 타입 동기화 필수

### Docker 환경 (Windows Docker Desktop 29.x)
- Docker Desktop 29.x는 API v1.40 미만 요청에 400 반환 → docker-java 기본값(v1.23) 사용 불가
- Gradle `test {}` 블록에 `environment 'DOCKER_API_VERSION', '1.41'` 설정 (OS 환경변수로 전달)
- `systemProperty` 는 JVM 프로퍼티 → docker-java가 읽지 않으므로 반드시 `environment` 사용
- `TESTCONTAINERS_RYUK_DISABLED=true` + `testcontainers.properties`의 `ryuk.disabled=true` 로 Ryuk 비활성화

## Consequences

- 장점: 실제 PostgreSQL에서 쿼리 검증 → 운영 이슈 사전 차단
- 장점: 레이어 책임이 명확하여 실패 원인 식별 용이
- 단점: Repository 테스트 시 Docker 필수 (CI에서도 Docker-in-Docker 필요)
- 단점: Testcontainers 컨테이너 기동 시간(~5초) 만큼 테스트 실행 시간 증가

## Limitations & Future Work
- 계약 테스트 (Pact / Spring Cloud Contract) — [운영]
- 성능 테스트 (k6) — [운영]
- Mutation Testing (PIT) — 차별화 가능

## 면접 답변 시나리오

> **Q: 테스트는 어떻게 작성했나요?**
> A: 테스트 피라미드 구조로 레이어별 책임에 맞는 도구를 선택했습니다. 도메인/서비스 레이어는 JUnit5 + Mockito 순수 단위 테스트로 빠른 피드백을 얻고, Repository 레이어는 Testcontainers PostgreSQL을 사용해 실제 DB 방언에서 쿼리를 검증합니다. E2E 흐름은 @SpringBootTest 통합 테스트로 커버합니다.

> **Q: MSA 통합 테스트는 어떻게 했나요?**
> A: 서비스 간 직접 호출 대신 Kafka 이벤트 기반으로 통신하기 때문에, 각 서비스는 독립적으로 @SpringBootTest 테스트를 작성하고 Kafka는 EmbeddedKafka로 대체합니다. 서비스 간 계약 테스트는 운영 단계에서 Spring Cloud Contract 도입을 고려 중입니다.

> **Q: 왜 H2 대신 Testcontainers를 쓰나요?**
> A: H2의 PostgreSQL 호환 모드는 완전하지 않아 JPQL 쿼리나 QueryDSL의 일부 함수가 운영과 다르게 동작할 수 있습니다. Testcontainers는 실제 postgres 이미지를 컨테이너로 띄우기 때문에 운영 환경과 동일한 조건에서 쿼리를 검증할 수 있습니다. 실행 시간이 약간 늘어나지만 운영 이슈를 테스트 단계에서 잡을 수 있어 trade-off 가치가 있다고 판단했습니다.
