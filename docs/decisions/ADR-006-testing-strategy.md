# ADR-006: 테스트 전략

## Status
🔲 템플릿 — Week 5~6 (전체 테스트 정비 시 작성)

## Date
TBD

---

> 테스트 작성 기준·도구·커버리지 등 상세 규칙은 [`rules/testing-rule.md`](../../rules/testing-rule.md) 가 단일 출처(SoT). 이 ADR 은 **선택의 근거**만 기록.

## Context
- 1인 토이 프로젝트 + MSA + Saga 환경에서 어떤 테스트 전략으로 가야 하는가
- 단위/슬라이스/통합/계약/성능 중 어디에 시간 투자할지

## Decision
- TBD (Week 5~6 작성)

## Considered Options
- A. 단위 테스트 비중 극대화 (90%+) — 빠른 피드백, 적은 통합 신뢰
- B. 테스트 피라미드 70/20/10 (테이블링 표준) — 균형
- C. 통합 테스트 비중 확대 — 신뢰성↑, 속도↓

## Consequences
- TBD

## Limitations & Future Work
- 계약 테스트 (Pact / Spring Cloud Contract) — [운영]
- 성능 테스트 (k6) — [운영]
- Mutation Testing (PIT) — 차별화 가능

## 면접 답변 시나리오
> Q: 테스트는 어떻게 작성했나요?
> A:

> Q: MSA 통합 테스트는 어떻게 했나요?
> A:

> Q: 왜 이런 비율로 테스트를 작성했나요?
> A:
