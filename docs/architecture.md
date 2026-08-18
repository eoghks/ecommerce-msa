# 서비스 아키텍처

## 전체 구성도

```
[Client: React]
       │
       ▼
[API Gateway] ── Spring Cloud Gateway, JWT 검증
       │
  ┌────┼─────────────────────┐
  │    │                     │
  ▼    ▼                     ▼
[Auth] [Product]          [Order]
  │       │                  │
  │   Redis(캐싱+Lock)     (이벤트 처리)
  │       │                  │
  └───────┴────[Kafka]──────┘
               │      │
               │      └─ 이벤트 기반 Saga (분산 트랜잭션)
               ▼
        PostgreSQL (서비스별 DB 분리)
               │
        [Monitoring]
     Spring Actuator 수집
```

## 서비스별 책임

| 서비스 | 포트 | DB | 역할 |
|--------|------|----|------|
| API Gateway | 8080 | - | 라우팅, 인증 토큰 검증, 내부 전용 경로 차단 |
| Auth Service | 8081 | auth_db | 회원가입/로그인/RBAC(USER·SELLER·ADMIN), 판매자 승격, 서비스 간 사용자 조회 |
| Product Service | 8082 | product_db | 상품 CRUD + 캐싱 + 재고 이벤트, 카테고리 관리, 검색 고도화(정렬·가격필터·자동완성), 리뷰·별점, 위시리스트 |
| Order Service | 8083 | order_db | 주문 처리 + 이벤트 발행/구독 (Saga Choreography), 장바구니, 배송지 주소록, 배송상태, 알림, 반품·환불, 관리자 매출 통계 집계, 실패주문 로그 |
| Monitoring | 8084 | - | 헬스체크, 지표 수집 |
| Frontend | 3000 | - | React + TypeScript SPA |

## 서비스 간 내부 통신 (X-Internal-Token)

서비스 간 직접 호출(게이트웨이를 경유하지 않는 East-West 트래픽)은 `X-Internal-Token` 헤더로 인증한다.
해당 엔드포인트는 게이트웨이 화이트리스트에서 제외(INTERNAL_ONLY)되어 외부에서 접근할 수 없다.

| 호출 방향 | 엔드포인트 | 용도 |
|-----------|-----------|------|
| Product → Auth | `GET /api/v1/auth/users?ids=` | 상품 목록/상세에 판매자 정보(요약) 표시 |
| Product → Order | `GET /api/v1/orders/internal/purchased` | 리뷰 작성 자격 판정 — 해당 사용자의 상품 구매 여부 인증 |

- 토큰 설정: `app.internal.token` (서비스별 동일 시크릿, 운영에서는 환경변수 주입)
- 토큰 불일치 시 각 서비스의 `InvalidInternalTokenException`으로 차단

## Redis 활용

| 용도 | Key 패턴 | TTL |
|------|----------|-----|
| JWT 블랙리스트 (강제 무효화 전용) | `blacklist:{tokenId}` | 토큰 잔여 시간 |
| Refresh Token | `refresh:{userId}` | 7일 |
| 상품 목록 캐싱 | `products:{category}` | 10분 |
| 장바구니 | `cart:{userId}` | 1일 |
| 이벤트 멱등 키 | `event:processed:{eventId}` | 1일 |

## Kafka 활용 (이벤트 기반 Saga)

| 토픽 | 발행자 | 구독자 | 주요 이벤트 |
|------|--------|--------|------------|
| `order-events` | Order Service | Product Service | `OrderCreated` |
| `product-events` | Product Service | Order Service | `StockReserved`, `StockReserveFailed`, `StockRestored` |
| `payment-events` | Payment Service (향후) | Order, Product | `PaymentCompleted`, `PaymentFailed` |

- 파티션 키: `correlationId`(주문ID) — 동일 주문 이벤트는 순서 보장
- 발행: 토이 단계 `@TransactionalEventListener(AFTER_COMMIT)` / 운영 단계 Outbox 패턴 (ADR-001 참조)
- 소비: 멱등 처리 (`eventId` 중복 방지) + DLQ

## 서비스별 주요 테이블

> 서비스별 DB 분리 — 서비스 간 참조는 FK 제약 없이 애플리케이션 정합으로 관리.

| 서비스 | 테이블 | 설명 |
|--------|--------|------|
| Auth (auth_db) | `users` | 계정·역할(USER/SELLER/ADMIN)·판매자 전화번호 |
| Product (product_db) | `category` | 카테고리 (ADMIN 관리) |
| Product | `product` | 상품 (판매자 seller_id, 판매상태 status, 평균별점 rating_avg·rating_count) |
| Product | `wishlist` | 위시리스트(찜) — UNIQUE(user_id, product_id) |
| Product | `review` | 상품 리뷰·별점 — UNIQUE(user_id, product_id) |
| Order (order_db) | `orders` | 주문 (주문상태 status, 배송상태 delivery_status, 배송지 스냅샷) |
| Order | `order_item` | 주문 항목 (판매자·항목상태·항목취소) |
| Order | `cart_item` | 장바구니 항목 (사용자/게스트) |
| Order | `address` | 저장형 배송지 주소록 (기본배송지 부분 유니크) |
| Order | `notification` | 인앱 알림 (주문·배송·반품) |
| Order | `return_request` | 반품 요청 (상태·사유, 활성 상태 항목당 1건 부분 유니크, 낙관적 락 version) |
| Order | `failed_order_log` | 실패(자동취소) 주문 로그 (ADMIN 조회) |

## 서비스 타임존 (KST 고정)

주문 생성 시각(`@CreatedDate LocalDateTime`)과 매출 통계 집계(`LocalDate` 기준)는 JVM 기본 타임존에 좌우된다.
컨테이너 기본값인 UTC로 기동되면 KST 00:00~09:00 주문이 전날로 기록되어 당일 집계가 최대 9시간 누락되므로,
order-service는 기동 시 JVM 기본 타임존을 `Asia/Seoul`로 고정한다(`support/ServiceTimeZone.applyDefault()`).
배포 환경에서는 로그·DB 세션 시각까지 일관되도록 `TZ=Asia/Seoul`도 함께 지정한다([배포 체크리스트](deploy-checklist.md) 7번).

> 다국어·통화·다중 타임존 대응은 적용 범위 밖이며 Roadmap v2.0 글로벌화 트랙으로 분리했다([MD-06](tradeoffs/MD-06-globalization-scope.md)).

## 기술 스택

- **Backend**: Spring Boot 3.x, Spring Security, Spring Data JPA, Spring Kafka
- **Gateway**: Spring Cloud Gateway
- **DB**: PostgreSQL 16.x
- **Cache**: Redis 7.x
- **Message Broker**: Apache Kafka
- **Frontend**: React 19 + TypeScript, Vite, Axios, Zustand, React Router, Tailwind CSS
- **Infra**: Docker, Docker Compose
