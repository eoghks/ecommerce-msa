# 동시성 제어 (재고 차감)

> **상태**: 🔲 템플릿 — Week 4 (Order Service 구현 시 작성)

---

## 배경
- 재고 차감 시 동시성 문제 시나리오
- 같은 상품에 여러 주문 동시 발생 → 음수 재고 발생 가능

## 후보 비교
| 방식 | 원리 | 장점 | 단점 |
|------|------|------|------|
| Pessimistic Lock | DB row lock (`SELECT ... FOR UPDATE`) | 강한 일관성 | 성능 저하, 데드락 위험 |
| Optimistic Lock | `@Version` 충돌 감지 후 재시도 | 동시성 좋음 | 충돌 잦으면 재시도 비용 |
| Redis Lock (분산락) | 외부 캐시 기반 락 | 빠름, MSA 친화 | TTL/스토밍 처리 필요 |
| Redis Atomic 연산 | `DECR` 원자성 활용 | 매우 빠름 | 영속성 한계, 정합성 보강 필요 |

## 선택과 근거
- 어떤 방식을 선택했고 왜
- 트래픽/일관성/성능 트레이드오프

## 적용 방식
- Product Service: 재고 차감 로직
- Lock 키 구조 (`stock:lock:{productId}`)
- 실패/타임아웃 처리
- Saga 보상 트랜잭션 연계

## 한계 및 향후 개선
- 단일 Redis 장애 대비 (Redlock 알고리즘)
- 핫스팟 상품에서 락 경쟁 완화 전략 (Redis Atomic + Async 정산)

## 면접 답변 시나리오
> Q: 재고 차감 동시성은 어떻게 처리했나요?
> A:

> Q: Pessimistic Lock 안 쓴 이유는?
> A:

> Q: Redis 장애 시엔?
> A:
