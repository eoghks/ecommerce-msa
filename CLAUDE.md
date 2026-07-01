# ecommerce-msa CLAUDE.md

## 프로젝트 개요
MSA 기반 이커머스 플랫폼 (이직용 포트폴리오)
- Backend: Java 21, Spring Boot 3.x, Spring Cloud Gateway, Spring Data JPA, Spring Kafka, Gradle
- Frontend: React 18, TypeScript, Vite, TanStack Query, Zustand, Tailwind CSS
- Infra: PostgreSQL 16, Redis 7, Apache Kafka, Docker
- 구조: 모노레포 (서비스별 하위 디렉토리)

## 글로벌 규칙 예외 (이 프로젝트)
- 글로벌 CLAUDE.md 의 **MyBatis / Dao / `#{}` 바인딩** 관련 규칙은 이 프로젝트 미적용 — Spring Data JPA 사용
- 레이어명: `Dao` 가 아닌 **`Repository`**
- `.jsp` 미사용 — `.tsx` `.ts` 사용 (TypeScript)

## 모노레포 구조
```
ecommerce-msa/
├── gateway/          # Spring Cloud Gateway
├── auth-service/     # 회원가입/로그인/RBAC
├── product-service/  # 상품 CRUD + Redis 캐싱 + 재고 차감 (Redis Lock)
├── order-service/    # 주문 처리 + Kafka 이벤트 발행/구독
├── monitoring/       # Actuator + Prometheus + Grafana 대시보드
├── common/           # 공용 모듈 (ProblemDetail, 이벤트 베이스, MDC 필터)
├── frontend/         # React + TypeScript
├── docker/           # Docker Compose
├── rules/            # 작업 규칙 문서
├── docs/             # 설계 문서
└── .github/          # PR 템플릿, 워크플로
```

## 서비스 포트

| 서비스 | 포트 |
|--------|------|
| gateway | 8080 |
| auth-service | 8081 |
| product-service | 8082 |
| order-service | 8083 |
| monitoring | 8084 |
| frontend (dev) | 3000 |
| PostgreSQL | 5433 (Docker host) |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8085 |

## 서비스별 패키지 구조 (공통)
```
com.eoghks.<service>/
├── controller/
├── service/
├── repository/
├── domain/          # JPA 엔티티
├── dto/
│   ├── request/
│   └── response/
├── event/           # Kafka 이벤트
├── exception/
├── config/
└── common/
```

## Rules 파일 로드 규칙 (필수)
작업 시작 전 아래 매핑에 따라 해당 rules 파일을 **Read 도구로 직접 읽은 후 작업한다.**
rules 파일을 읽지 않고 코드 작성·커밋·문서 수정을 시작하면 안 된다.
복수 유형의 작업(예: 코드 구현 + Git 커밋)은 해당하는 모든 파일을 읽는다.

| 작업 유형 | 읽어야 할 rules 파일 |
|----------|-------------------|
| 코드 구현 | `rules/coding-rule.md`, `rules/naming-convention.md` |
| 보안 관련 구현 | `rules/security-rule.md` |
| 테스트 작성 / 테스트 결과 문서화 | `rules/testing-rule.md` |
| Git 커밋 / 브랜치 / PR | `rules/git-convention.md`, `rules/pr-rule.md` |
| 문서 작성 / 이력 관리 | `rules/docs-rule.md` |
| 프론트엔드 작업 | `rules/frontend-rule.md` |
| Docker / CI / 배포 | `rules/ops-rule.md` |

---

## 작업 워크플로우 (필수)
기능 구현 전 반드시 아래 순서를 따른다. **컨펌 없이 코드 작성 금지.**

```
1. 설계 문서 작성 (docs/services/<service>/<파일>.md)
2. 사용자 컨펌 대기
3. 컨펌 후 → feature 브랜치 생성 → 구현 → 테스트 → develop 머지
```

- 서비스 문서는 해당 주차 코딩 시작 전에 생성 (Week 2 시작 전 auth/, Week 3 시작 전 product/ 등)
- 설계 변경 발생 시 코드보다 문서 먼저 수정 → 재컨펌 후 반영
- 문서 작성 규칙: [docs-rule.md](rules/docs-rule.md)

## 작업 규칙
모든 규칙은 `rules/` 폴더에서 관리합니다.

| 파일 | 내용 |
|------|------|
| [coding-rule.md](rules/coding-rule.md) | Java/Spring 코딩 규칙, 레이어 책임 |
| [security-rule.md](rules/security-rule.md) | 인증/인가, 데이터 보안, OWASP |
| [git-convention.md](rules/git-convention.md) | 브랜치 전략, 커밋 메시지 |
| [naming-convention.md](rules/naming-convention.md) | 클래스/메서드/REST URL/DB/Redis/Kafka 네이밍 |
| [testing-rule.md](rules/testing-rule.md) | 테스트 작성 기준, 피라미드, 커버리지 (단일 출처) |
| [pr-rule.md](rules/pr-rule.md) | PR 템플릿, 라벨, CI 게이트 |
| [frontend-rule.md](rules/frontend-rule.md) | React 컴포넌트, TanStack Query, 토큰 처리 |
| [ops-rule.md](rules/ops-rule.md) | 환경 분리, Docker, CI/CD, 모니터링 |
| [docs-rule.md](rules/docs-rule.md) | 서비스 문서 작성 가이드, 템플릿, 인덱스 관리 |

## 교차 주제 (여러 규칙 참조)
| 주제 | 관련 규칙 |
|------|----------|
| MSA 통신 보안 | coding-rule + security-rule |
| 토큰 처리 | security-rule + frontend-rule |
| 테스트 동시 작성 | coding-rule + testing-rule (testing-rule 단일 출처) |
| ADR 운영 | docs/decisions/README.md |

## 토이 vs 운영 태그
- `[토이 필수]` — 7주 안에 무조건 적용
- `[운영]` — 학습 가치는 있지만 토이 단계 미적용 (별도 문서 학습)
- 태그 적용 대상: `coding-rule`, `security-rule`, `testing-rule`, `ops-rule` 4개 문서
- 나머지(`naming`, `git`, `pr`, `frontend`)는 항목 전체가 토이·운영 공통 적용이라 태그 미사용
