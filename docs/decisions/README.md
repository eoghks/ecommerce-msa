# 기술 결정 문서 (ADR — Architecture Decision Records)

이 프로젝트의 주요 기술 선택과 트레이드오프를 기록합니다.
일반 지식이 아닌 **이 프로젝트에서 왜 그렇게 결정했는가**가 핵심.

## 작성 원칙
- 결정한 시점에 즉시 작성 (사후 회고 X)
- 비교한 후보, 선택 근거, 한계점 명시
- 면접 답변 시나리오 포함
- 한 번 작성된 ADR 은 **수정 금지** — 결정이 바뀌면 새 ADR 작성 후 이전 ADR `Status: Superseded by ADR-NNN`

## 파일명 규칙
- 형식: `ADR-NNN-<kebab-case-주제>.md`
- 번호는 작성 순서대로 1씩 증가 (재사용 금지)

## ADR 템플릿
```markdown
# ADR-NNN: <제목>

## Status
- Proposed | Accepted | Superseded by ADR-NNN

## Date
- YYYY-MM-DD

## Context
무슨 문제를 해결하려는가, 배경과 제약

## Decision
무엇을 선택했는가 (한 줄 + 짧은 근거)

## Considered Options
- 후보 A — 장단점
- 후보 B — 장단점
- 후보 C — 장단점

## Consequences
선택의 결과 — 긍정적 + 부정적 영향

## Limitations & Future Work
한계, 알려진 위험, 나중에 바꿀 가능성

## 면접 답변 시나리오
> Q: ...
> A: ...
```

## 문서 현황

| ADR | 상태 | 작성 시점 |
|-----|------|----------|
| [ADR-001 — Saga Pattern (Choreography)](ADR-001-saga-pattern.md) | ✅ Accepted | Week 0 |
| [ADR-002 — JWT 설계](ADR-002-jwt-design.md) | 🔲 템플릿 | Week 2 |
| [ADR-003 — DB 설계](ADR-003-db-design.md) | 🔲 템플릿 | Week 3 |
| [ADR-004 — 동시성 제어](ADR-004-concurrency-control.md) | 🔲 템플릿 | Week 4 |
| [ADR-005 — 트랜잭션 격리](ADR-005-transaction-isolation.md) | 🔲 템플릿 | Week 4 |
| [ADR-006 — 테스트 전략](ADR-006-testing-strategy.md) | 🔲 템플릿 | Week 5~6 |

> 보류 항목(Rate Limiting, 응답 헤더, CI/CD)은 [`docs/study/`](../study/README.md) 참고
