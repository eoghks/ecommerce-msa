# 테스트 규칙

## 원칙
- 구현 코드와 테스트는 **동시 작성** (사후 작성 금지)
- 커밋 전 전체 통과 — 실패 시 수정 후 재실행
- 보고 형식: `통과 N건 / 실패 0건`
- 한 테스트는 **한 가지만 검증** (assertion 분산 금지)
- AssertJ 사용 (`assertThat` 체이닝, JUnit `assertEquals` 대신)
- Given-When-Then 주석으로 단계 명시

## 테스트 범위
| 레이어 | 도구 | 범위 |
|--------|------|------|
| Service | JUnit 5 + Mockito | 비즈니스 로직 단위 테스트 |
| Repository | `@DataJpaTest` + Testcontainers PostgreSQL | JPA 쿼리 검증 |
| Controller | `@WebMvcTest` + MockMvc | API 요청/응답 검증 |
| 통합 (E2E) | `@SpringBootTest` + Testcontainers | 핵심 시나리오 |

## 외부 의존성 처리
- **단위 테스트**: Mockito 로 Mock
- **Repository 테스트**: H2 대신 **Testcontainers PostgreSQL** (실제 DB와 동일하게 검증)
- **Kafka 테스트**: `@EmbeddedKafka` (간단) 또는 Testcontainers Kafka (운영 유사)
- **Redis 테스트**: Testcontainers Redis

## Saga / 이벤트 통합 테스트
- 주문 생성 → 재고 차감 → 결과 이벤트 흐름 검증
- 보상 트랜잭션(재고 부족 → 주문 취소) 검증
- `awaitility` 로 비동기 결과 대기

## 테스트 격리
- 통합 테스트는 트랜잭션 rollback 또는 `@Sql` 로 데이터 초기화
- `@DirtiesContext` 는 비용 큼 — 꼭 필요할 때만
- 테스트 간 상태 공유 금지

## 네이밍
- 클래스: `<대상>Test`
- 메서드: `<대상메서드>_<상황>_<기대결과>` (한국어 가능)

## 픽스처
- `fixture/` 패키지에 공통 객체 빌더 관리
- 메서드별 격리된 데이터 사용

## 커버리지
- 핵심 도메인 (Service): 80% 이상
- 전체: 70% 이상
- JaCoCo 리포트로 측정, CI에 포함

## 실행 분리 (Gradle)
- `test` — 단위·슬라이스 테스트만 (빠름)
- `integrationTest` — Testcontainers 사용 통합 테스트 (느림, CI 전용)
- 로컬 개발 중엔 `test` 위주 실행

## 상세 전략
- [docs/decisions/testing-strategy.md](../docs/decisions/testing-strategy.md) (Week 5~6 작성)
