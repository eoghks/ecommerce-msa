# Product Service API

> ADMIN 전용 엔드포인트는 Gateway가 주입한 `X-User-Role: ADMIN` 헤더로 권한 체크.

---

## 상품 등록 (ADMIN)

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

## 상품 수정 (ADMIN)

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

## 상품 삭제 (ADMIN)

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
| size | int | 20 | 페이지 크기 (최대 100) |
| sort | string | createdAt,desc | 정렬 |
| categoryId | Long | — | 카테고리 필터 |
| keyword | string | — | 상품명 키워드 검색 |

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

## 카테고리 목록 조회

### `GET /api/v1/categories`

**Response** `200 OK`
```json
[
  { "id": 1, "name": "전자기기" },
  { "id": 2, "name": "의류" }
]
```
