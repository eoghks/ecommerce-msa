# 기술 결정 문서 (Decision Records)

이 프로젝트의 주요 기술 선택과 트레이드오프를 기록합니다.
일반 지식이 아닌 **이 프로젝트에서 왜 그렇게 결정했는가**가 핵심.

## 작성 원칙
- 결정한 시점에 즉시 작성 (사후 회고 X, 결정 시 기록)
- 비교한 후보, 선택 근거, 한계점 명시
- 면접 답변 시나리오 포함

## 문서 현황

| 문서 | 상태 | 작성 시점 |
|------|------|----------|
| [saga-pattern.md](saga-pattern.md) | ✅ 완료 | Week 0 |
| [jwt-design.md](jwt-design.md) | 🔲 템플릿 | Week 2 (Auth Service) |
| [db-design.md](db-design.md) | 🔲 템플릿 | Week 3 (Product Service) |
| [concurrency-control.md](concurrency-control.md) | 🔲 템플릿 | Week 4 (Order Service) |
| [transaction-isolation.md](transaction-isolation.md) | 🔲 템플릿 | Week 4 (Order Service) |
| [testing-strategy.md](testing-strategy.md) | 🔲 템플릿 | Week 5~6 |

> 보류된 항목(Rate Limiting, 응답 헤더 보안)은 [`docs/study/`](../study/README.md) 참고

## 표준 템플릿
```
## 배경
무슨 문제를 해결하려는가

## 후보 비교
A / B / C 비교 표

## 선택과 근거
무엇을 선택했고 왜

## 적용 방식
이 프로젝트에서 구체적 구현

## 한계 및 향후 개선
단점, 알려진 위험, 나중에 바꿀 수 있는 부분

## 면접 답변 시나리오
Q: ...
A: ...
```
