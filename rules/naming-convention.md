# 네이밍 컨벤션

> 패키지 구조는 [CLAUDE.md](../CLAUDE.md) 참조

## 클래스
- Spring 표준 따름 (`<도메인>Controller`, `<도메인>Service`, `<도메인>Repository`, `<대상>Config`, `<대상>Test`)
- Entity: 단수 명사 (`User`, `Order`)
- Request DTO: `<동사><도메인>Request` (`LoginRequest`, `CreateProductRequest`)
- Response DTO: `<도메인>Response` (`UserResponse`)
- Exception: `<도메인><이유>Exception` (`StockInsufficientException`)
- Event: `<도메인><동사ed>Event` (`OrderCreatedEvent`)

## 메서드
- **Service public (유스케이스)**
  - 조회 동사는 `find` 로 통일 (`findUserById`, `findProducts`)
  - CRUD 표준 동사 사용 (`create` / `update` / `delete` / `validate`)
  - 목록 조회는 복수형 (`findProducts`)
- **Service private (헬퍼)**
  - 위 규칙 강제 안 함, 구체적 행위 동사로 자유롭게
  - 예: `reserveStock`, `loadAuthenticatedUser`, `buildPendingOrder`, `toResponse`

## 변수/필드
- camelCase
- Boolean: `is<상태>` (`isDeleted`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 컬렉션: 복수형 (`orderItems`)
- enum 값: UPPER_SNAKE_CASE (`OrderStatus.PAID`)

## DB
- 테이블: 단수 snake_case (`user`, `order_item`)
- 컬럼: snake_case (`created_at`, `user_id`)
- PK: `id` / FK: `<참조테이블>_id`
- 시각 컬럼: `created_at`, `updated_at` (전 테이블 공통)
- 인덱스: `idx_<테이블>_<컬럼>`
- 유니크: `uk_<테이블>_<컬럼>`

## Kafka
- 토픽: `<도메인>-events` (`order-events`)
- 컨슈머 그룹: `<service>-<topic>-consumer`
- 이벤트 클래스: `<도메인><동사ed>Event`

## Frontend
- 상세: [frontend-rule.md](frontend-rule.md)
