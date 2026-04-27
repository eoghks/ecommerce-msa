# 네이밍 컨벤션

## 패키지 구조 (서비스별 공통)
```
com.eoghks.<service>/
├── controller/
├── service/
├── repository/
├── domain/          # JPA 엔티티
├── dto/
│   ├── request/
│   └── response/
├── event/           # Kafka 이벤트 클래스
├── exception/
├── config/
└── common/
```

## 클래스 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| Controller | `<도메인>Controller` | `AuthController` |
| Service | `<도메인>Service` | `AuthService` |
| Repository | `<도메인>Repository` | `UserRepository` |
| Entity | 단수 명사 | `User`, `Order` |
| Request DTO | `<동사><도메인>Request` | `LoginRequest` |
| Response DTO | `<도메인>Response` | `UserResponse` |
| Exception | `<도메인><이유>Exception` | `StockInsufficientException` |
| Config | `<대상>Config` | `SecurityConfig` |
| Event | `<도메인><동사ed>Event` | `OrderCreatedEvent`, `StockReservedEvent` |
| Test | `<대상>Test` | `AuthServiceTest` |

## 메서드 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| 단건 조회 | `find<도메인>By<조건>` | `findUserById` |
| 목록 조회 | `find<도메인>s` | `findProducts` |
| 생성 | `create<도메인>` | `createOrder` |
| 수정 | `update<도메인>` | `updateProduct` |
| 삭제 | `delete<도메인>` | `deleteProduct` |
| 검증 | `validate<대상>` | `validateToken` |

## 변수/필드
- camelCase
- Boolean: `is<상태>` (`isDeleted`, `isAdmin`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 컬렉션: 복수형 (`products`, `orderItems`)
- enum 값: UPPER_SNAKE_CASE (`OrderStatus.PAID`)

## DB
- 테이블명: 단수 snake_case (`user`, `order_item`)
- 컬럼: snake_case (`created_at`, `user_id`)
- PK: `id` / FK: `<참조테이블>_id`
- 시각 컬럼: `created_at`, `updated_at` (전 테이블 공통)
- 인덱스: `idx_<테이블>_<컬럼>` (`idx_order_user_id`)
- 유니크: `uk_<테이블>_<컬럼>` (`uk_user_email`)

## Kafka
- 토픽: `<도메인>-events` (`order-events`, `product-events`)
- 컨슈머 그룹: `<service>-<topic>-consumer` (`product-order-events-consumer`)
- 이벤트 클래스: `<도메인><동사ed>Event`

## Frontend
- 상세: [frontend-rule.md](frontend-rule.md)
