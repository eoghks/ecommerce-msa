# 주간 개발 로드맵

> 하루 2시간 / 주 5일 / 총 7주  
> 스택: Java 21, Spring Boot 3.5, Gradle, PostgreSQL, Redis, React 19, Docker

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
| 1 | Docker Compose 작성 (PostgreSQL, Redis, Kafka+Zookeeper, 네트워크 구성) |
| 2 | Spring Cloud Gateway 프로젝트 생성 (Gradle, Java 21) |
| 3 | 공통 모듈 — `ProblemDetail` 핸들러, 공통 이벤트 베이스 클래스 |
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
| 4 | Refresh Token 구현 + Redis 저장 / 로그아웃 (Refresh 무효화) |
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

## Week 4 — Order Service + Saga (Kafka) ✅
> 브랜치: `feature/order-service`

| Day | 작업 |
|-----|------|
| 1 | 주문 엔티티 설계 (주문/주문상품), PostgreSQL 연결 |
| 2 | Kafka 토픽 구성, Spring Kafka 설정 (Producer/Consumer) |
| 3 | 주문 생성 API + `OrderCreated` 이벤트 발행 (Outbox 패턴) |
| 4 | Product Service 재고 차감 컨슈머 + Redis Lock 동시성 처리 + 결과 이벤트 발행 |
| 5 | 보상 이벤트 처리 (StockReserveFailed → 주문 CANCELLED) + 테스트 + PR → develop 머지 |

---

## Week 5 — React 프론트엔드
> 브랜치: `feature/frontend`

| Day | 작업 |
|-----|------|
| 1 ✅ | React 프로젝트 생성, 라우터/Axios 기본 세팅, 로그인·회원가입·내 정보 화면, Navbar |
| 2 ✅ | 비밀번호 찾기 화면(PR #14), 상품 목록/상세 화면, Gateway 안정화(JWKS fastRecovery + Optional 인증), 로그인 UX 개선 |
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

---

## Version 2 — Auth Service 기능 확장 (7주 이후)

> 7주 완성 후 추가 개발 예정. auth-service 사용자 셀프 서비스 기능.

| 기능 | 설명 | 비고 |
|------|------|------|
| 내 정보 조회 | `GET /api/v1/users/me` — X-User-Id 헤더로 본인 조회 | |
| 비밀번호 변경 | `PATCH /api/v1/users/me/password` — 현재 비밀번호 확인 후 변경 | BCrypt 재암호화 |
| 회원 정보 수정 | `PATCH /api/v1/users/me` — 이름 등 수정 | |
| 회원 탈퇴 | `DELETE /api/v1/users/me` — 소프트 삭제 (`deleted_at`) | |
| 내 주문 내역 | `GET /api/v1/users/me/orders` — Order Service 연동 | OpenFeign |

### 구현 시 작업 목록

- [ ] auth-service `RoleHeaderAuthenticationFilter` 추가 (product-service와 동일 패턴)
- [ ] `SecurityConfig` 인증 필요 엔드포인트 설정
- [ ] `UserController` — `/api/v1/users/me` 엔드포인트 추가
- [ ] `UserService` — 비밀번호 변경 시 현재 비밀번호 검증 로직
- [ ] `User` 엔티티 — `deleted_at` 필드 추가 (소프트 삭제)
- [ ] Flyway V2 마이그레이션 — `deleted_at` 컬럼 추가
- [ ] Gateway 라우팅 — `/api/v1/users/**` 추가

---

## Backlog — 추후 구현 예정

> 7주 로드맵 완료 후 우선순위에 따라 순차 진행.

---

### B-01. 판매자(SELLER) 역할 및 판매 플로우

**개요**: USER → 판매자 신청 → 전화번호 인증 → SELLER 승격 → 상품 등록/판매/정산

**구현 범위**

| 단계 | 내용 |
|------|------|
| 판매자 신청 | 내 정보 화면에서 "판매자 신청" 버튼 → 전화번호 입력 → SMS 인증번호 발송 |
| 본인 인증 | 인증번호 확인 후 `SELLER` 역할 승격 (auth-service) |
| 상품 관리 | SELLER는 본인이 등록한 상품만 수정/삭제 가능 (상품에 `sellerId` 필드 추가) |
| 정산 | 주문 완료 시 판매금액의 일정 비율을 SELLER 정산 계좌에 적립 (정산 서비스 별도) |

**필요 작업**
- [ ] `Role` 열거형에 `SELLER` 추가
- [ ] `User` 엔티티에 `phone`, `sellerAppliedAt` 필드 추가
- [ ] SMS 인증 서비스 연동 (Twilio 또는 NHN Cloud SMS)
- [ ] `POST /api/v1/auth/seller/apply` — 판매자 신청 + SMS 발송
- [ ] `POST /api/v1/auth/seller/verify` — 인증번호 확인 + 역할 승격
- [ ] `Product` 엔티티에 `sellerId` 추가, SELLER는 본인 상품만 관리
- [ ] JWT 클레임에 `role: SELLER` 반영
- [ ] 정산 서비스 (settlement-service) 신규 생성
- [ ] 프론트: 내 정보 화면에 "판매자 신청" UI 추가

---

### B-02. 마일리지

**개요**: 주문 완료 시 결제금액의 일정 비율 마일리지 적립, 다음 주문 시 사용

**구현 범위**
- 적립: 주문 확정 시 결제금액의 1% 자동 적립
- 사용: 주문 시 보유 마일리지 전액 또는 일부 차감
- 내역: 적립/사용 이력 조회

**필요 작업**
- [ ] `mileage-service` 신규 생성 (또는 auth-service 확장)
- [ ] `Mileage` 엔티티 — userId, amount, type(EARN/USE), createdAt
- [ ] Kafka 이벤트 연동 — `OrderConfirmed` 이벤트 수신 후 마일리지 적립
- [ ] `GET /api/v1/mileage/me` — 잔액 및 내역 조회
- [ ] `POST /api/v1/mileage/use` — 사용 요청 (주문 서비스에서 호출)
- [ ] 프론트: 주문 화면에 마일리지 사용 UI, 내 정보에 잔액 표시

---

### B-03. 쿠폰

**개요**: ADMIN이 쿠폰 발행, USER가 다운로드 후 주문 시 적용

**구현 범위**
- 발행: ADMIN이 정률/정액 할인 쿠폰 생성 (유효기간, 최소 주문금액, 최대 할인금액)
- 다운로드: USER가 쿠폰 목록에서 발급
- 사용: 주문 시 보유 쿠폰 선택 → 할인 적용 (1회용, 사용 후 소멸)

**필요 작업**
- [ ] `coupon-service` 신규 생성
- [ ] `Coupon` 엔티티 — 쿠폰 정의 (코드, 타입, 금액, 유효기간, 발급 수량)
- [ ] `UserCoupon` 엔티티 — 유저별 보유/사용 현황
- [ ] `POST /api/v1/coupons` (ADMIN) — 쿠폰 생성
- [ ] `POST /api/v1/coupons/{id}/download` — 쿠폰 다운로드
- [ ] `GET /api/v1/coupons/me` — 내 쿠폰 목록
- [ ] 주문 생성 시 쿠폰 코드 전달 → order-service에서 coupon-service 검증 호출
- [ ] 프론트: 쿠폰함 화면, 주문 시 쿠폰 선택 UI

---

### B-04. 프론트엔드 TypeScript 마이그레이션

**개요**: 현재 JavaScript(JSX)로 작성된 프론트엔드를 TypeScript(TSX)로 전환

**전환 범위**
| 작업 | 대상 |
|------|------|
| 파일 리네임 | `.jsx` → `.tsx`, `.js` → `.ts` (약 25개) |
| 타입 정의 | API 응답 타입 (`Product`, `Order`, `User`, `Category` 등) |
| 스토어 타입 | Zustand `authStore`, `cartStore` 인터페이스 정의 |
| 컴포넌트 props | 각 컴포넌트 props 타입 명시 |
| `tsconfig.json` | Vite + React 기준 설정 |

**사전 조건**
- `@types/react`, `@types/react-dom` 이미 설치됨 (`devDependencies`)
- `typescript` 패키지 설치 필요 (`npm i -D typescript`)

**예상 소요**: 1일 (파일 변환 + 타입 에러 수정)

**우선순위**: 낮음 — 7주 기능 완성 후 리팩토링 단계에서 진행

---

## 기술 스택 확정
| 항목 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Gradle |
| DB | PostgreSQL 16 |
| Cache | Redis 7.x |
| Frontend | React 19, Vite 8, Tailwind CSS |
| Infra | Docker, Docker Compose |
