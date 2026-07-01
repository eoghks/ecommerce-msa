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
| description | TEXT | | 상품 설명 |
| price | DECIMAL(12,2) | NOT NULL | 가격 (`BigDecimal` 사용) |
| stock | INT | NOT NULL, DEFAULT 0 | 재고 수량 |
| imageUrl | VARCHAR(500) | | 대표 이미지 URL |
| category | FK → Category | NOT NULL | 카테고리 |
| createdAt | TIMESTAMP | NOT NULL | |
| updatedAt | TIMESTAMP | NOT NULL | |

### 인덱스
- `category_id` — 카테고리별 조회
- `name` — 키워드 검색 (`LIKE` 또는 Full-Text)
- `created_at DESC` — 최신순 정렬

### 연관관계
- `Product` N ↔ 1 `Category` (ManyToOne, FetchType.LAZY)

### 도메인 규칙
- 가격은 `BigDecimal` — `double`/`float` 금지
- 재고는 음수 불가 — 차감 전 검증 필수
- 삭제는 물리 삭제 (이력 필요 시 소프트 삭제 [운영])

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
└── V1__init_schema.sql   # category, product 테이블 생성
```
