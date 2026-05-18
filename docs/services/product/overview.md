# Product Service Overview

## 역할
상품 등록·수정·삭제·조회, 카테고리 관리, Redis 캐싱, 재고 관리를 담당하는 서비스.
ADMIN만 상품을 등록/수정/삭제할 수 있으며, 조회는 인증 없이 가능.

## 기본 정보

| 항목 | 값 |
|------|----|
| 포트 | 8082 |
| DB | product_db (PostgreSQL 16, Docker port 5433) |
| Redis | 상품 목록·상세 캐싱 (TTL 10분), 재고 분산락 |
| 의존 서비스 | gateway (라우팅·인증), order-service (재고 차감 이벤트 수신) |

## 주요 기술

- **JPA + PostgreSQL**: 상품·카테고리 엔티티 관리
- **Flyway**: DB 마이그레이션
- **Redis 캐싱 (Cache-Aside)**: 상품 목록·상세 TTL 10분, 쓰기 시 캐시 삭제
- **Redis 분산락 (Redisson)**: 재고 차감 동시성 제어 (Week 4 연동)
- **Kafka Consumer**: OrderCreatedEvent 수신 → 재고 차감 (Week 4)
- **Spring Security `@PreAuthorize`**: AOP 기반 메서드 레벨 인가 (`ROLE_ADMIN` 체크)
- **커스텀 예외**: `ProductNotFoundException` → 404, `AuthorizationDeniedException` → 403 (`GlobalExceptionHandler` 처리)

## 구현 현황

### Day 1
- [x] Product, Category 엔티티 설계
- [x] Flyway 마이그레이션 (`V1__init_schema.sql`)
- [x] product_db PostgreSQL 연결

### Day 2
- [x] 상품 등록 API (`POST /api/v1/products`) — ADMIN 전용
- [x] 상품 수정 API (`PUT /api/v1/products/{id}`) — ADMIN 전용
- [x] 상품 삭제 API (`DELETE /api/v1/products/{id}`) — ADMIN 전용

### Day 3
- [x] 상품 목록 조회 API (`GET /api/v1/products`) — Redis 캐싱 TTL 10분
- [x] 상품 상세 조회 API (`GET /api/v1/products/{id}`) — Redis 캐싱 TTL 10분

### Day 4
- [x] 카테고리별 필터링 (`?categoryId=1`)
- [x] 키워드 검색 (`?keyword=노트북`)

### Day 5
- [x] 재고 필드 추가 (`stock`)
- [x] 단위 테스트 + Testcontainers 통합 테스트 + PR → develop 머지
