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

### B-05. 카테고리 관리 (ADMIN 전용)

**개요**: ADMIN 상품 관리 화면에 카테고리 탭 추가. 판매자는 기존 카테고리 선택만 가능.

**구현 범위**
- ADMIN 화면에 "카테고리" 탭 추가
- 카테고리 추가/수정/삭제 API (`POST/PUT/DELETE /api/v1/categories`) — ADMIN 전용
- SELLER는 드롭다운 선택만 허용 (현재 구조 유지)

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

## Roadmap v1.1 — 이커머스 실서비스형 기능 확장

> 7주 기능 + 코드리뷰 하드닝 완료 이후 단계. 실제 이커머스에 "있으면 좋은" 기능을 우선순위로 정리.
> 기존 Backlog(B-02 마일리지 / B-03 쿠폰 / B-04 TS / B-05 카테고리)와 함께 진행.

### 우선순위 요약

| # | 기능 | 규모 | 핵심 가치 |
|---|------|------|----------|
| V1.1-1 | 상품 리뷰·별점 | 중 | 구매 결정 근거, 상품 신뢰도 |
| V1.1-2 | 위시리스트(찜) | 소 | 재방문·전환율 |
| V1.1-3 | 배송지 관리 + 배송 상태 | 중 | 주문 완결성(주문→배송) |
| V1.1-4 | 알림(주문·배송 상태) | 중 | 리텐션, M-3 실시간 알림과 연계 |
| V1.1-5 | 반품·환불 | 중~대 | 주문 라이프사이클 완성 |
| V1.1-6 | 결제(PG) 연동 | 대 | mock 결제 → 실제 결제 흐름 |
| V1.1-7 | 관리자 매출 통계 대시보드 | 중 | 운영 가시성 |
| V1.1-8 | 상품 검색 고도화(자동완성·정렬) | 소~중 | 탐색성 |

---

### V1.1-1. 상품 리뷰·별점
**개요**: 구매 확정한 주문 항목에 한해 별점(1~5) + 텍스트 리뷰 작성. 상품 상세에 평균 별점·리뷰 목록 노출.

**필요 작업**
- [ ] `Review` 엔티티 — productId, userId, orderItemId, rating, content, createdAt (구매 인증: 해당 유저의 CONFIRMED 주문 항목만 작성 허용)
- [ ] `POST /api/v1/products/{id}/reviews` (작성, 1주문항목 1리뷰), `GET .../reviews` (목록/페이징)
- [ ] 상품 평균 별점 집계 — 조회 성능 위해 product에 ratingAvg/ratingCount 비정규화 컬럼 + 리뷰 변경 시 갱신
- [ ] 프론트: 상품 상세 리뷰 탭, 내 주문에서 "리뷰 쓰기" 버튼
- [ ] 욕설/스팸 최소 방어(길이 제한, XSS 이스케이프)

### V1.1-2. 위시리스트(찜)
**개요**: 상품을 찜 목록에 추가/삭제, 마이페이지에서 조회. 로그인 사용자 한정.

**필요 작업**
- [ ] `Wishlist` 저장 — Redis(Set: `wishlist:{userId}`) 또는 경량 테이블
- [ ] `POST/DELETE /api/v1/wishlist/{productId}`, `GET /api/v1/wishlist/me`
- [ ] 프론트: 상품 카드/상세 하트 토글, 마이페이지 찜 목록

### V1.1-3. 배송지 관리 + 배송 상태
**개요**: 사용자 배송지 주소록(다건, 기본 배송지). 주문 시 배송지 선택. 주문에 배송 상태(준비중→배송중→완료) 추가.

**필요 작업**
- [ ] `Address` 엔티티 — userId, 수령인, 전화, 주소, isDefault
- [ ] `CRUD /api/v1/addresses` (본인 소유 검증)
- [ ] 주문 생성 시 배송지 스냅샷 저장(주소 변경돼도 주문 이력 보존)
- [ ] Order에 `deliveryStatus`(PREPARING/SHIPPING/DELIVERED) + 관리자/판매자 상태 변경 API
- [ ] 프론트: 배송지 관리 화면, 주문 시 선택, 주문 상세에 배송 상태 표시

### V1.1-4. 알림 (주문·배송 상태)
**개요**: 주문 확정/취소/배송 상태 변경 시 사용자 알림. M-3에서 미룬 실시간 알림 축을 여기서 정식화.

**필요 작업**
- [ ] `notification-service` 신규 또는 order 확장 — Kafka 이벤트(OrderConfirmed/Cancelled/Shipped) 구독
- [ ] `Notification` 저장 + `GET /api/v1/notifications/me`, 읽음 처리
- [ ] 전달 채널: 우선 인앱(폴링/SSE), 이후 이메일 확장(기존 MailService 재사용)
- [ ] 운영 알림(DLT 적재 등)도 이 축에 통합 → M-3 실시간 알림 해소
- [ ] 프론트: Navbar 알림 뱃지 + 목록

### V1.1-5. 반품·환불
**개요**: 배송 완료 주문 항목에 대해 사용자가 반품 신청 → 판매자/관리자 승인 → 환불 처리(재고 복구 포함).

**필요 작업**
- [ ] `ReturnRequest` 엔티티 — orderItemId, reason, status(REQUESTED/APPROVED/REJECTED/REFUNDED)
- [ ] 반품 신청/승인/거부 API + 상태 전이 검증
- [ ] 승인 시 재고 복구(기존 항목취소 Saga 재사용) + 결제 취소(V1.1-6 PG 연동 시 실제 환불)
- [ ] 프론트: 내 주문 반품 신청, 관리자 반품 관리 화면

### V1.1-6. 결제(PG) 연동
**개요**: 현재 mock 결제를 실제 PG(토스페이먼츠/아임포트 등 샌드박스)로 전환. 결제 승인 후 주문 확정.

**필요 작업**
- [ ] `payment-service` 또는 order 확장 — 결제 요청/승인/취소
- [ ] PG 샌드박스 연동(결제창 → 콜백 검증 → 승인). **키/시크릿은 env, 로그 마스킹**
- [ ] 결제 승인 이벤트로 주문 상태 전이(결제대기→결제완료→재고차감 Saga)
- [ ] 환불(V1.1-5)과 연동한 결제 취소
- [ ] 프론트: 결제 화면, 결제 결과 처리

### V1.1-7. 관리자 매출 통계 대시보드
**개요**: 기간별 매출/주문수/취소율, 상품별·판매자별 매출 상위, 실패주문 추이를 관리자 화면에 시각화.

**필요 작업**
- [ ] 집계 쿼리(기간/상품/판매자) — 성능 위해 일별 집계 배치 또는 조회 캐싱 검토
- [ ] `GET /api/v1/admin/stats/*` (ADMIN)
- [ ] 프론트: 관리자 대시보드(차트) — 기존 모니터링/실패주문 화면과 통합 탭

### V1.1-8. 상품 검색 고도화
**개요**: 키워드 자동완성, 정렬(최신/가격/인기/별점), 가격대 필터. 규모 커지면 검색엔진 도입 검토.

**필요 작업**
- [ ] 정렬/필터 파라미터 확장(기존 목록 API)
- [ ] 자동완성 — 인기 검색어/상품명 prefix(Redis) 우선, 이후 Elasticsearch 검토(트레이드오프 문서화)
- [ ] 프론트: 검색바 자동완성, 정렬/필터 UI

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
