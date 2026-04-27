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
  └───────┴──────────────────┘
               │
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
| Product Service | 8082 | product_db | 상품 CRUD + 캐싱 |
| Order Service | 8083 | order_db | 주문/결제/재고 |
| Monitoring | 8084 | - | 헬스체크, 지표 수집 |
| Frontend | 3000 | - | React SPA |

## Redis 활용

| 용도 | Key 패턴 | TTL |
|------|----------|-----|
| JWT 블랙리스트 | `blacklist:{token}` | 토큰 만료 시간 |
| 상품 목록 캐싱 | `products:{category}` | 10분 |
| 장바구니 | `cart:{userId}` | 1일 |
| 재고 Lock | `stock:lock:{productId}` | 3초 |

## 기술 스택

- **Backend**: Spring Boot 3.x, Spring Security, Spring Data JPA
- **Gateway**: Spring Cloud Gateway
- **DB**: PostgreSQL 16.x
- **Cache**: Redis 7.x
- **Frontend**: React 18, Axios
- **Infra**: Docker, Docker Compose
