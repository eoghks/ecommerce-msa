# Product Service 엔티티

## Category

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | |
| name | VARCHAR(50) | NOT NULL, UNIQUE | 카테고리명 (예: 전자기기, 의류) |
| createdAt | TIMESTAMP | NOT NULL | |
| updatedAt | TIMESTAMP | NOT NULL | |

### 인덱스
- `name` — UNIQUE 인덱스

### 연관관계
- `Category` 1 ↔ N `Product`

---

## Product

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | |
| name | VARCHAR(200) | NOT NULL | 상품명 |
| description | VARCHAR(1000) | | 상품 설명 |
| price | BIGINT | NOT NULL | 원화 가격 (`Long` — 소수점 없음) |
| stock | INT | NOT NULL, DEFAULT 0 | 재고 수량 |
| imageUrl | VARCHAR(500) | | 대표 이미지 URL |
| sellerId | BIGINT | | 판매자 ID (null이면 ADMIN 등록 상품) |
| status | VARCHAR(20) | NOT NULL | 판매 상태 (ACTIVE/BANNED) |
| category | FK → Category | NOT NULL | 카테고리 |
| ratingAvg | NUMERIC(2,1) | NOT NULL, DEFAULT 0.0 | 평균 별점 (리뷰 변경 시 재계산·비정규화) |
| ratingCount | INT | NOT NULL, DEFAULT 0 | 리뷰 개수 (비정규화) |
| createdAt | TIMESTAMP | NOT NULL | |
| updatedAt | TIMESTAMP | NOT NULL | |

### 인덱스
- `category_id` — 카테고리별 조회
- `name` — 자동완성 prefix 검색·이름 정렬 (V8, 부분일치 `%kw%`는 활용 불가)
- `created_at DESC` — 최신순 정렬

### 연관관계
- `Product` N ↔ 1 `Category` (ManyToOne, FetchType.LAZY)

### 도메인 규칙
- 가격은 원화 `Long` — 소수점 없음
- 재고는 음수 불가 — 차감 전 검증 필수
- 삭제는 물리 삭제 (이력 필요 시 소프트 삭제 [운영])
- 판매금지(BANNED) 상품은 공개 목록/상세 노출·신규 주문 대상에서 제외
- SELLER는 본인 상품(`sellerId` 일치)만 수정/삭제 가능

---

## Review (V1.1-1)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | |
| productId | BIGINT | NOT NULL | 대상 상품 (서비스 내 참조, FK 없음) |
| userId | BIGINT | NOT NULL | 작성자 |
| rating | SMALLINT | NOT NULL, CHECK 1~5 | 별점 |
| content | VARCHAR(1000) | | 리뷰 내용 |
| createdAt | TIMESTAMP | NOT NULL | |
| updatedAt | TIMESTAMP | | 수정 시에만 갱신 (최초 null) |

### 인덱스 / 제약
- `UNIQUE(user_id, product_id)` — 1인 1리뷰 (수정으로 갱신)
- `idx_review_product (product_id, created_at DESC)` — 상품별 최신순 조회

### 도메인 규칙
- 작성은 구매 인증 필요 (order-service `/orders/internal/purchased` 내부 호출)
- 리뷰 변경 시 `product.rating_avg`·`rating_count` 재계산 + 상세 캐시 무효화

---

## Wishlist (V1.1-2)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | |
| userId | BIGINT | NOT NULL | 찜한 사용자 |
| productId | BIGINT | NOT NULL | 찜한 상품 |
| createdAt | TIMESTAMP | NOT NULL | |

### 인덱스 / 제약
- `UNIQUE(user_id, product_id)` — 중복 찜 방지(멱등)
- `idx_wishlist_user (user_id, created_at DESC)` — 내 찜 목록 최신순

---

## Redis 캐싱 구조

| Key | Value | TTL |
|-----|-------|-----|
| `product:detail:{productId}` | 상품 상세 JSON | 10분 |

> 목록 캐시는 조건 조합이 무한대라 캐시 히트율이 낮고 관리 복잡도만 높아 제외.
> 메인 페이지 베스트/추천 상품 등 고정 목록 캐시는 추후 버전에서 추가 예정.

### 캐싱 정책 (Cache-Aside)
- 조회 시 캐시 우선 → 미스 시 DB 조회 후 캐시 저장
- 상품 수정/삭제 시 `product:detail:{id}` 단건 삭제
- 상세는 `product:cache.md` 참조

---

## Flyway 마이그레이션

```
product-service/src/main/resources/db/migration/
├── V1__init_schema.sql          # category, product 테이블 생성
├── V2__add_seller_id.sql        # product.seller_id (판매자)
├── V3__add_product_status.sql   # product.status (ACTIVE/BANNED)
├── V7__wishlist.sql             # wishlist 테이블 (V1.1-2)
├── V8__product_name_index.sql   # product.name 인덱스 (자동완성/이름정렬)
└── V9__review_and_rating.sql    # review 테이블 + product.rating_avg/rating_count (V1.1-1)
```
