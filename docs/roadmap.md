# 주간 개발 로드맵

> 하루 2시간 / 주 5일 / 총 7주  
> 스택: Java 21, Spring Boot 3.x, Gradle, PostgreSQL, Redis, React, Docker

---

## Week 0 — 프로젝트 세팅 ✅
| Day | 작업 |
|-----|------|
| 1 | 프로젝트 디렉토리 생성, CLAUDE.md 작성 |
| 2 | docs/ 구조 설계 (architecture, api-spec, roadmap) |
| 3 | .gitignore, README 작성 |
| 4 | GitHub 레포 생성, main/develop 브랜치 push |
| 5 | 브랜치 전략 최종 확인, Week 1 준비 |

---

## Week 1 — 인프라 세팅
> 브랜치: `feature/infra-setup`

| Day | 작업 |
|-----|------|
| 1 | Docker Compose 작성 (PostgreSQL, Redis, 네트워크 구성) |
| 2 | Spring Cloud Gateway 프로젝트 생성 (Gradle, Java 21) |
| 3 | 공통 모듈 — 응답 포맷(`ApiResponse`), 예외 처리(`GlobalException`) |
| 4 | 각 서비스 Spring Boot 프로젝트 스캐폴딩 (auth/product/order/monitoring) |
| 5 | AI Agent 환경 구성 (Claude Code 플러그인 세팅) + PR → develop 머지 |

---

## Week 2 — Auth Service
> 브랜치: `feature/auth-service`

| Day | 작업 |
|-----|------|
| 1 | 회원 엔티티 설계 (JPA), PostgreSQL 연결, Flyway 마이그레이션 |
| 2 | 회원가입 API (비밀번호 BCrypt 암호화) |
| 3 | 로그인 API — JWT Access Token 발급 |
| 4 | Refresh Token 구현 + Redis 저장 / 로그아웃 블랙리스트 처리 |
| 5 | RBAC 권한 구조 (USER/ADMIN) + 테스트 작성 + PR → develop 머지 |

---

## Week 3 — Product Service
> 브랜치: `feature/product-service`

| Day | 작업 |
|-----|------|
| 1 | 상품 엔티티/카테고리 설계, PostgreSQL 연결 |
| 2 | 상품 CRUD API (등록/수정/삭제 — ADMIN 전용) |
| 3 | 상품 목록 조회 + Redis 캐싱 (TTL 10분) |
| 4 | 카테고리별 필터링, 키워드 검색 |
| 5 | 재고 필드 추가 + 테스트 작성 + PR → develop 머지 |

---

## Week 4 — Order Service
> 브랜치: `feature/order-service`

| Day | 작업 |
|-----|------|
| 1 | 주문 엔티티 설계 (주문/주문상품), PostgreSQL 연결 |
| 2 | 주문 생성 API — Product Service 재고 확인 연동 |
| 3 | Redis Lock 기반 재고 차감 동시성 처리 |
| 4 | 주문 상태 관리 (PENDING→PAID→SHIPPED→DONE), 주문 취소 |
| 5 | 주문 목록/상세 조회 + 테스트 작성 + PR → develop 머지 |

---

## Week 5 — React 프론트엔드
> 브랜치: `feature/frontend`

| Day | 작업 |
|-----|------|
| 1 | React 프로젝트 생성, 라우터/Axios 기본 세팅, 로그인·회원가입 화면 |
| 2 | 상품 목록/상세 화면 |
| 3 | 장바구니 (Redis 기반) + 주문하기 화면 |
| 4 | 관리자 화면 — 상품 등록/수정/삭제 |
| 5 | 관리자 화면 — 주문 목록 조회 + PR → develop 머지 |

---

## Week 6 — Monitoring + 마무리
> 브랜치: `feature/monitoring`

| Day | 작업 |
|-----|------|
| 1 | Spring Actuator 설정, 각 서비스 헬스체크 엔드포인트 |
| 2 | Monitoring Service — 서비스 상태 수집 및 대시보드 API |
| 3 | React 관리자 화면에 모니터링 탭 추가 |
| 4 | 통합 테스트, 버그 수정 |
| 5 | README 최종 완성, develop → main PR 머지 |

---

## 기술 스택 확정
| 항목 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle |
| DB | PostgreSQL |
| Cache | Redis 7.x |
| Frontend | React 18 |
| Infra | Docker, Docker Compose |
