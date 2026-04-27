# ecommerce-msa CLAUDE.md

## 프로젝트 개요
MSA 기반 이커머스 플랫폼 (이직용 포트폴리오)
- 스택: Java 21, Spring Boot 3.x, Spring Data JPA, React 18, Docker, PostgreSQL 16, Redis 7, Gradle
- 구조: 모노레포 (서비스별 하위 디렉토리)

## 서비스 구조
```
ecommerce-msa/
├── gateway/          # Spring Cloud Gateway
├── auth-service/     # 회원가입/로그인/RBAC
├── product-service/  # 상품 CRUD + Redis 캐싱
├── order-service/    # 주문/재고 + Redis Lock
├── monitoring/       # Actuator 기반 대시보드
├── frontend/         # React
├── docker/           # Docker Compose
├── rules/            # 작업 규칙 문서
└── docs/             # 설계 문서
```

## 작업 규칙
모든 규칙은 `rules/` 폴더에서 관리합니다.

| 파일 | 내용 |
|------|------|
| [coding-rule.md](rules/coding-rule.md) | Java/Spring 코딩 규칙, 레이어 책임 |
| [security-rule.md](rules/security-rule.md) | 인증/인가, 데이터 보안, 환경변수 |
| [git-convention.md](rules/git-convention.md) | 브랜치 전략, 커밋 메시지 규칙 |
| [naming-convention.md](rules/naming-convention.md) | 패키지/클래스/변수/DB 네이밍 |
| [testing-rule.md](rules/testing-rule.md) | 테스트 작성 기준, 패턴 |
| [pr-rule.md](rules/pr-rule.md) | PR 템플릿, 머지 전 체크리스트 |
| [frontend-rule.md](rules/frontend-rule.md) | React 컴포넌트 구조, 네이밍 |
