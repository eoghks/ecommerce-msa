# 네이밍 컨벤션

## 패키지 구조 (서비스별 동일하게 적용)

```
com.eoghks.<service>/
├── controller/
├── service/
├── repository/
├── domain/          # JPA 엔티티
├── dto/
│   ├── request/
│   └── response/
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
| Entity | 단수 명사 | `User`, `Product`, `Order` |
| Request DTO | `<동사><도메인>Request` | `LoginRequest`, `CreateProductRequest` |
| Response DTO | `<도메인>Response` | `UserResponse`, `ProductResponse` |
| Exception | `<도메인><이유>Exception` | `UserNotFoundException`, `StockInsufficientException` |
| Config | `<대상>Config` | `SecurityConfig`, `RedisConfig` |

## 메서드 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| 조회 (단건) | `find<도메인>By<조건>` | `findUserById` |
| 조회 (목록) | `find<도메인>s` | `findProducts` |
| 생성 | `create<도메인>` | `createOrder` |
| 수정 | `update<도메인>` | `updateProduct` |
| 삭제 | `delete<도메인>` | `deleteProduct` |
| 검증 | `validate<대상>` | `validateToken` |

## 변수/필드 네이밍
- camelCase 사용
- Boolean 변수: `is<상태>` (예: `isDeleted`, `isAdmin`)
- 상수: UPPER_SNAKE_CASE (예: `MAX_RETRY_COUNT`)
- List/Collection: 복수형 (예: `products`, `orderItems`)

## DB 컬럼 네이밍
- snake_case 사용 (예: `created_at`, `user_id`)
- PK: `id`
- FK: `<참조테이블>_id` (예: `user_id`, `product_id`)
- 생성/수정 시각: `created_at`, `updated_at` (모든 테이블 공통)
