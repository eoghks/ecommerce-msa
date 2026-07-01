# 문서 작성 규칙

## 원칙
- 코드보다 문서가 먼저 — 설계 문서 작성 → 컨펌 → 코드 작성
- 구현이 변경되면 문서도 함께 업데이트
- 모든 서비스 문서는 `docs/services/<service>/` 하위에 관리

---

## 서비스 문서 생성 타이밍

| 주차 | 생성 문서 |
|------|----------|
| Week 2 시작 전 | `docs/services/auth/` |
| Week 3 시작 전 | `docs/services/product/` |
| Week 4 시작 전 | `docs/services/order/` |
| Week 6 시작 전 | `docs/services/monitoring/` |

---

## 서비스 문서 구조

```
docs/services/<service>/
├── overview.md   # 역할, 포트, DB, 의존 서비스
├── entity.md     # JPA 엔티티, 테이블 설계
├── api.md        # 엔드포인트 명세 (요청/응답/에러)
└── <주제>.md     # 서비스 특화 문서 (flow, cache, saga 등)
```

### 서비스별 특화 문서

| 서비스 | 특화 문서 | 내용 |
|--------|----------|------|
| auth | `flow.md` | JWT 발급·갱신·무효화 흐름, RBAC |
| product | `cache.md` | Redis 캐싱 정책, 재고 Lock 전략 |
| order | `saga.md` | Kafka Saga Choreography 흐름도 |
| monitoring | — | overview + api 로 충분 |

---

## 파일별 작성 템플릿

### overview.md
```markdown
# <Service> Overview

## 역할
(서비스가 하는 일 1~3줄)

## 기본 정보
| 항목 | 값 |
|------|----|
| 포트 | 80XX |
| DB | <db_name> |
| 의존 서비스 | (없음 / gateway, auth 등) |

## 주요 기술
- (Redis 캐싱, Kafka 발행 등 핵심 기술 나열)

## 구현 현황
- [ ] 엔티티 설계
- [ ] 회원가입 API
- [ ] ...
```

### entity.md
```markdown
# <Service> 엔티티

## <EntityName>
| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | |
| ... | | | |

### 연관관계
- (관계 설명)

### 인덱스
- (인덱스 설명)
```

### api.md
```markdown
# <Service> API

## <기능명>

### `POST /api/v1/<resource>`

**Request**
```json
{
  "field": "value"
}
```

**Response** `201 Created`
```json
{
  "field": "value"
}
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 400 | 입력값 오류 |
| 409 | 중복 |
```

---

## docs/index.md 관리 규칙

- 새 기능 구현 시 `docs/index.md` 크로스 레퍼런스 테이블에 행 추가
- 형식: `기능명 | 구현 파일 | 서비스 문서 | 관련 규칙`
- 파일 경로는 레포 루트 기준 상대경로 사용

---

## 이력 문서 관리 규칙

### 리팩토링 이력 (`docs/refactor-history/`)
- 리팩토링 작업 후 반드시 날짜별 파일 생성: `docs/refactor-history/YYYY-MM-DD.md`
- 파일 형식: `날짜 · 대상 서비스 · 항목/원인/수정 내용` 표 형식
- `OVERVIEW.md`에 날짜·대상 서비스 한 줄 추가

### 트레이드오프 (`docs/tradeoffs/`)
- 의도적으로 스킵한 기술 부채는 주제별 파일로 분리: `{ID}-{slug}.md`
- 파일 형식: `현재 구현 · 문제점 · 운영 수준 해결책 · 포트폴리오 선택 이유 · 면접 답변 포인트`
- `OVERVIEW.md`에 ID·주제·대상 서비스 한 줄 추가
- ID 심각도 코드: CR(Critical) · HR(High) · MD(Medium) · LW(Low), 뒤 숫자는 같은 등급 내 순서
