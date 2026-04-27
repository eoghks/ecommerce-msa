# ecommerce-msa CLAUDE.md

## 프로젝트 개요
MSA 기반 이커머스 플랫폼 (이직용 포트폴리오)
- Backend: Java 21, Spring Boot 3.x, Spring Cloud Gateway, Spring Data JPA, Spring Kafka, Gradle
- Frontend: React 18, TypeScript, Vite, TanStack Query, Zustand, Tailwind CSS
- Infra: PostgreSQL 16, Redis 7, Apache Kafka, Docker
- 구조: 모노레포 (서비스별 하위 디렉토리)

## 모노레포 구조
```
ecommerce-msa/
├── gateway/          # Spring Cloud Gateway
├── auth-service/     # 회원가입/로그인/RBAC
├── product-service/  # 상품 CRUD + Redis 캐싱 + 재고 차감 (Redis Lock)
├── order-service/    # 주문 처리 + Kafka 이벤트 발행/구독
├── monitoring/       # Actuator 기반 대시보드
├── frontend/         # React
├── docker/           # Docker Compose
├── rules/            # 작업 규칙 문서
└── docs/             # 설계 문서
```

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

## 교차 주제 (여러 규칙 참조)
| 주제 | 관련 규칙 |
|------|----------|
| MSA 통신 보안 | coding-rule + security-rule |
| 토큰 처리 | security-rule + frontend-rule |
| 테스트 동시 작성 | coding-rule + testing-rule (testing-rule 단일 출처) |
| ADR 운영 | docs/decisions/README.md |

## 토이 vs 운영 태그
규칙 문서 일부 항목에 `[토이 필수]` / `[운영]` 태그가 있어요.
- `[토이 필수]` — 7주 안에 무조건 적용
- `[운영]` — 학습 가치는 있지만 토이 단계 미적용 (별도 문서 학습)
