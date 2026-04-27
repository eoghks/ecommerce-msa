# 테스트 전략

> **상태**: 🔲 템플릿 — Week 5~6 (전체 테스트 정비 시 작성)

---

## 배경
- 테스트 피라미드 — 단위 / 통합 / E2E 비율
- MSA 환경에서 통합 테스트의 어려움

## 레이어별 전략
| 레이어 | 도구 | 범위 | 비율 목표 |
|--------|------|------|----------|
| Service (단위) | JUnit 5 + Mockito | 비즈니스 로직 | 70% |
| Repository (슬라이스) | `@DataJpaTest` + H2 | 쿼리 검증 | 15% |
| Controller (슬라이스) | `@WebMvcTest` + MockMvc | 요청/응답 | 10% |
| 통합 (E2E) | `@SpringBootTest` + Testcontainers | 핵심 시나리오 | 5% |

## 핵심 시나리오 통합 테스트
- 회원가입 → 로그인 → 상품 조회 → 주문 생성 → 재고 확인
- Kafka 이벤트 흐름 검증 (`@EmbeddedKafka` 또는 Testcontainers Kafka)
- Saga 보상 트랜잭션 (재고 부족 시 주문 취소)

## 외부 의존성 처리
- DB: Testcontainers PostgreSQL
- Kafka: Testcontainers Kafka 또는 `@EmbeddedKafka`
- Redis: Testcontainers Redis

## 픽스처 / 데이터
- 공통 픽스처는 `fixture/` 패키지에 관리
- 테스트 데이터는 메서드별로 격리

## 커버리지 목표
- 핵심 도메인 (Service): 80% 이상
- 전체: 70% 이상
- JaCoCo 리포트로 측정

## 한계 및 향후 개선
- E2E는 빠른 피드백 어려워서 핵심만 유지
- Contract 테스트 (Pact) 도입 검토
- 성능 테스트 (k6 / JMeter) 별도 단계

## 면접 답변 시나리오
> Q: 테스트는 어떻게 작성했나요?
> A:

> Q: MSA 통합 테스트는 어떻게 했나요?
> A:

> Q: Testcontainers는 왜 사용했나요?
> A:
