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
  │    Redis(캐싱)        Redis(Lock)
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
| API Gateway | 8080 | - | 라우팅, 인증 토큰 검증 |
| Auth Service | 8081 | auth_db | 회원가입/로그인/RBAC |
| Product Service | 8082 | product_db | 상품 CRUD + 캐싱 + 재고 이벤트 |
| Order Service | 8083 | order_db | 주문 처리 + 이벤트 발행/구독 (Saga Choreography) |
| Monitoring | 8084 | - | 헬스체크, 지표 수집 |
| Frontend | 3000 | - | React SPA |

## Redis 활용

| 용도 | Key 패턴 | TTL |
|------|----------|-----|
| JWT 블랙리스트 | `blacklist:{token}` | 토큰 만료 시간 |
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
- 발행: Outbox 패턴으로 DB 트랜잭션 원자성 보장
- 소비: 멱등 처리 (`eventId` 중복 방지) + DLQ

## 기술 스택

- **Backend**: Spring Boot 3.x, Spring Security, Spring Data JPA, Spring Kafka
- **Gateway**: Spring Cloud Gateway
- **DB**: PostgreSQL 16.x
- **Cache**: Redis 7.x
- **Message Broker**: Apache Kafka
- **Frontend**: React 18, Axios
- **Infra**: Docker, Docker Compose
