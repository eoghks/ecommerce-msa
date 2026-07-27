# Product Service API

> 권한 체크는 Gateway가 주입한 `X-User-Role` / `X-User-Id` 헤더 기반 `@PreAuthorize`.
> 상품 쓰기(등록/수정/삭제)는 ADMIN(전체) 또는 SELLER(본인 상품)만 가능하며, 판매금지(ban/unban)와 카테고리 관리는 ADMIN 전용.

---

## 상품 등록 (ADMIN/SELLER)

### `POST /api/v1/products`

**Request**
```json
{
  "name": "맥북 프로 14인치",
  "description": "Apple M3 Pro 칩 탑재",
  "price": 2990000,
  "stock": 50,
  "imageUrl": "https://...",
  "categoryId": 1
}
```

**Response** `201 Created`
```json
{
  "id": 1,
  "name": "맥북 프로 14인치",
  "price": 2990000,
  "stock": 50,
  "categoryId": 1,
  "createdAt": "2026-05-11T00:00:00Z"
}
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 400 | 입력값 오류 (price ≤ 0, name 공백 등) |
| 403 | ADMIN 아닌 사용자 |
| 404 | categoryId 존재하지 않음 |

---

## 상품 수정 (ADMIN/SELLER)

### `PUT /api/v1/products/{id}`

**Request**
```json
{
  "name": "맥북 프로 14인치 (업데이트)",
  "description": "...",
  "price": 2790000,
  "stock": 30,
  "imageUrl": "https://...",
  "categoryId": 1
}
```

**Response** `200 OK` — 수정된 상품 정보

**Error**
| 상태코드 | 사유 |
|---------|------|
| 403 | ADMIN 아닌 사용자 |
| 404 | 상품 없음 |

---

## 상품 삭제 (ADMIN/SELLER)

### `DELETE /api/v1/products/{id}`

**Response** `204 No Content`

**Error**
| 상태코드 | 사유 |
|---------|------|
| 403 | ADMIN 아닌 사용자 |
| 404 | 상품 없음 |

---

## 상품 목록 조회

### `GET /api/v1/products`

**Query Params**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| sort | string | latest | 정렬 화이트리스트: `latest`/`price_asc`/`price_desc`/`name` (미허용값은 `latest` 폴백) |
| categoryId | Long | — | 카테고리 필터 |
| keyword | string | — | 상품명 키워드 검색 (부분일치) |
| minPrice | Long | — | 최소 가격 (0 이상) |
| maxPrice | Long | — | 최대 가격 (minPrice 이상) |
| includeBanned | boolean | false | ADMIN 전용 — 판매금지 상품 포함 + 판매자 정보 노출 |

> 공개 목록은 항상 판매금지(BANNED) 상품 제외. `minPrice > maxPrice` 또는 음수는 `400`.

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "name": "맥북 프로 14인치",
      "price": 2990000,
      "stock": 50,
      "imageUrl": "https://...",
      "categoryId": 1,
      "categoryName": "전자기기"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

> Redis 캐싱 적용 (TTL 10분). 쓰기 작업 발생 시 캐시 무효화.

---

## 상품 상세 조회

### `GET /api/v1/products/{id}`

**Response** `200 OK`
```json
{
  "id": 1,
  "name": "맥북 프로 14인치",
  "description": "Apple M3 Pro 칩 탑재",
  "price": 2990000,
  "stock": 50,
  "imageUrl": "https://...",
  "categoryId": 1,
  "categoryName": "전자기기",
  "createdAt": "2026-05-11T00:00:00Z",
  "updatedAt": "2026-05-11T00:00:00Z"
}
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 404 | 상품 없음 |

---

## 상품명 자동완성

### `GET /api/v1/products/suggestions`

판매중 상품명 prefix 후보를 반환.

**Query Params**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| keyword | string | — | trim 후 1자 미만이면 빈 목록 |
| limit | int | 10 | 최대 20 (초과 시 20으로 캡) |

**Response** `200 OK`
```json
["맥북 프로 14인치", "맥북 에어 13인치"]
```

---

## 카테고리 CRUD

### `GET /api/v1/categories` (공개)

**Response** `200 OK`
```json
[
  { "id": 1, "name": "전자기기" },
  { "id": 2, "name": "의류" }
]
```

### `POST /api/v1/categories` (ADMIN) — `201 Created`
### `PUT /api/v1/categories/{id}` (ADMIN) — `200 OK`
### `DELETE /api/v1/categories/{id}` (ADMIN) — `204 No Content`

**Request** (등록/수정)
```json
{ "name": "가전" }
```

---

## 리뷰·별점 (V1.1-1)

> Base: `/api/v1/products/{productId}/reviews`. 목록은 공개, 작성/수정/삭제는 인증(X-User-Id) 필요.

### `GET /api/v1/products/{productId}/reviews`
최신순 페이징(기본 size 10).

**Response** `200 OK` — `Page<ReviewResponse>`
```json
{
  "content": [
    { "reviewId": 10, "productId": 1, "userId": 5, "rating": 5,
      "content": "만족합니다", "createdAt": "2026-07-01T10:00:00", "updatedAt": null }
  ],
  "totalElements": 1, "totalPages": 1
}
```

### `POST /api/v1/products/{productId}/reviews` — `201 Created`
구매자만(order 내부 구매 인증) + 1인 1리뷰.

**Request**
```json
{ "rating": 5, "content": "만족합니다" }
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | 미인증 / 미구매 |
| 404 | 상품 없음 |
| 409 | 이미 작성한 리뷰(1인1리뷰) |

### `PUT /api/v1/products/{productId}/reviews/{reviewId}` (본인) — `200 OK`
### `DELETE /api/v1/products/{productId}/reviews/{reviewId}` (본인/ADMIN) — `204 No Content`

> 리뷰 작성/수정/삭제 시 상품 평균 별점 재계산 + 상세 캐시(`product:detail:{id}`) 무효화.

---

## 위시리스트(찜) (V1.1-2)

> Base: `/api/v1/wishlist`. 모두 인증(X-User-Id) 필요 — 없으면 `401`.

### `POST /api/v1/wishlist/{productId}` — `204 No Content` (멱등 추가)
### `DELETE /api/v1/wishlist/{productId}` — `204 No Content` (멱등 해제)

### `GET /api/v1/wishlist/me`
내 찜 목록(최신순 페이징).

**Response** `200 OK` — `Page<WishlistItemResponse>`
```json
{
  "content": [
    { "productId": 1, "name": "맥북 프로 14인치", "price": 2990000,
      "imageUrl": "https://...", "status": "판매중", "createdAt": "2026-07-01T10:00:00" }
  ],
  "totalElements": 1, "totalPages": 1
}
```

### `GET /api/v1/wishlist/me/ids`
하트 표시용 찜 상품 ID 집합.

**Response** `200 OK`
```json
[1, 5, 8]
```
