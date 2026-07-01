# 트랜잭션 격리 수준

> **상태**: 🔲 템플릿 — Week 4 (Order Service 구현 시 작성)

---

## 배경
- DB 격리 수준이 무엇이고 왜 중요한가
- 4단계: READ_UNCOMMITTED / READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE
- 각 단계에서 발생/방지 되는 현상 (Dirty Read, Non-Repeatable Read, Phantom Read)

## 선택
- PostgreSQL 기본값: `READ_COMMITTED`
- 이 프로젝트에서 채택한 기본 격리 수준
- 특정 케이스에서 `@Transactional(isolation = ...)` 변경 필요한 메서드

## 적용 방식
- 일반 조회/저장: 기본값 사용
- 재고 차감 같은 민감 트랜잭션: 격리 수준 + 락 조합
- 보고서/통계 조회: `REPEATABLE_READ` 검토

## Saga와의 관계
- 분산 트랜잭션은 격리 수준이 아니라 보상으로 일관성 보장
- 단일 서비스 내부는 격리 수준, 서비스 간은 Saga
- "최종적 일관성" (Eventually Consistent) 의미

## 한계 및 향후 개선
- SERIALIZABLE은 성능 저하 커서 채택 안 함
- 핫 데이터는 캐시로 격리 부담 완화

## 면접 답변 시나리오
> Q: 어느 격리 수준을 사용했나요? 왜?
> A:

> Q: Phantom Read 가 뭐고 어떻게 막을 수 있나요?
> A:

> Q: Saga 환경에서 격리 수준이 어떤 의미를 갖나요?
> A:
