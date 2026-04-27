# 네이밍 컨벤션

> 패키지 구조는 [CLAUDE.md](../CLAUDE.md) 참조

## 클래스
- Spring 표준 따름 (`<도메인>Controller`, `<도메인>Service`, `<도메인>Repository`, `<대상>Config`, `<대상>Test`)
- Entity: 단수 명사 (`User`, `Order`)
- Request DTO: `<동사><도메인>Request` (`LoginRequest`, `CreateProductRequest`)
- Response DTO: `<도메인>Response` (`UserResponse`)
- Exception: `<도메인><이유>Exception` (`StockInsufficientException`)
- Event: `<도메인><PastParticiple>Event` — 발생한 사실을 과거분사로 (`OrderCreatedEvent`, `StockReservedEvent`)
- 보상 이벤트: `<도메인>CompensatedEvent` 또는 도메인별 명시 (`StockRestoredEvent`)

## 메서드
- **Service public — 유스케이스 단위**
  - 조회: 결과가 없을 수 있으면 `find` (Optional 반환), 반드시 존재해야 하면 `get` (없으면 예외)
    - `findUserById` → `Optional<User>`
    - `getUserById` → `User` (없으면 `UserNotFoundException`)
  - 생성/수정/삭제/검증: `create` / `update` / `delete` / `validate` (CRUD 표준)
  - 목록 조회는 복수형 (`findProducts`)
- **Service private — 헬퍼**
  - prefix 권장: `build` (객체 조립), `validate` (검증), `to` (변환), `load` (조회), `publish` (이벤트), `apply` (상태 변경)
  - 그 외 자유, 구체적 행위 동사

## 변수/필드
- camelCase
- Boolean: `is<상태>` (`isDeleted`), 보유는 `has<대상>` (`hasItems`), 가능 여부는 `can<동작>` (`canCancel`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 컬렉션: 복수형 (`orderItems`)
- enum 값: UPPER_SNAKE_CASE (`OrderStatus.PAID`)

### Lombok 함정
- Boolean 필드명에 `is` 접두사 사용 시 `@Getter` 가 `isDeleted()` 생성 — JSON 직렬화 시 필드명 불일치 가능
- 권장: 필드명은 `deleted`, getter 만 `isDeleted()` 자동 생성되게 두기

## REST API URL
- 형식: `/api/v{버전}/<리소스(복수형, kebab-case)>/{id}/<하위리소스>`
- 예: `/api/v1/products`, `/api/v1/orders/{orderId}/items`, `/api/v1/order-items`
- 동사 사용 금지, 리소스는 복수형

## DB
- 테이블: 단수 snake_case (`user`, `order_item`)
- 컬럼: snake_case (`created_at`, `user_id`)
- PK: `id` / FK: `<참조테이블>_id`
- 자기참조: `parent_<테이블>_id` (`parent_category_id`)
- 다중 참조 동일 테이블: `<역할>_<참조>_id` (`created_by_user_id`, `assigned_to_user_id`)
- 시각: `created_at`, `updated_at` (전 테이블 공통)
- 인덱스: `idx_<테이블>_<컬럼1>[_<컬럼2>...]` (`idx_order_user_id_created_at`)
- 유니크: `uk_<테이블>_<컬럼>`

### Entity ↔ DB 매핑
- `order_item` (테이블) ↔ `OrderItem` (Entity) ↔ `orderItems` (컬렉션 필드)

## Redis
- 키 네임스페이스: `<service>:<domain>:<식별자>`
- 예: `auth:refresh:{userId}`, `product:cache:{productId}`, `order:idempotency:{key}`
- TTL 명시 필수 (영구 보관 키는 별도 정책)

## Kafka
- 토픽: `<도메인>-events` (`order-events`, `product-events`)
- DLT 토픽: `<원토픽>.DLT` (`order-events.DLT`)
- 컨슈머 그룹: `<service>-<topic>-consumer` (`product-order-events-consumer`)
- 이벤트 클래스: 위 클래스 규칙 참조

## 로그 / MDC
- MDC 키: `requestId`, `userId`, `correlationId` (이벤트 추적용)
- 로그 메시지: 한국어 가능, 변수는 `{}` 플레이스홀더

## 컨벤션 강제
- (선택) ArchUnit / Checkstyle 룰로 레이어 위반·네이밍 위반 자동 검출

## Frontend
- 상세: [frontend-rule.md](frontend-rule.md)
