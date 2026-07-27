# Order Service API

> 권한 체크는 Gateway가 주입한 `X-User-Id` / `X-User-Role` 헤더 기반.
> `/orders/internal/**`는 게이트웨이 화이트리스트에서 제외된 서비스 간 직접 호출 전용(X-Internal-Token).

---

## 주문 생성

### `POST /api/v1/orders` (USER)

배송지는 저장형 주소(`addressId`) 선택 또는 직접 입력 중 하나. `addressId` 지정 시 해당 주소 값을 스냅샷으로 복사한다.

**Request**
```json
{
  "items": [ { "productId": 1, "quantity": 2 } ],
  "addressId": 10
}
```
또는 직접 입력(하위호환):
```json
{
  "items": [ { "productId": 1, "quantity": 2 } ],
  "receiver": "홍길동",
  "phone": "010-1234-5678",
  "address": "서울시 ..."
}
```

**Response** `201 Created` — `OrderResponse`
```json
{
  "id": 1,
  "userId": 5,
  "status": "PENDING",
  "deliveryStatus": "PREPARING",
  "totalPrice": 5980000,
  "items": [ { "productId": 1, "quantity": 2 } ],
  "receiver": "홍길동",
  "phone": "010-1234-5678",
  "address": "서울시 ...",
  "createdAt": "2026-07-01T10:00:00"
}
```

> 생성 직후 PENDING → 재고 확보 Saga 성공 시 CONFIRMED, 실패 시 CANCELLED.

**Error**
| 상태코드 | 사유 |
|---------|------|
| 400 | 입력값 검증 실패 / 배송지(addressId·직접입력) 모두 없음 |
| 401 | 인증 없음 |
| 409 | 재고 부족 |

---

## 주문 조회

### `GET /api/v1/orders/me` (USER) — 내 주문 목록(페이징)
### `GET /api/v1/orders/{orderId}` (USER) — 주문 상세 (본인만)
### `GET /api/v1/orders/admin` (ADMIN) — 전체 주문 목록
### `GET /api/v1/orders/seller` (SELLER) — 판매자 주문 목록 (본인 상품 항목만, 합계도 재계산)

**Error**
| 상태코드 | 사유 |
|---------|------|
| 403 | 본인 주문 아님 |
| 404 | 주문 없음 |

---

## 주문 취소

### `DELETE /api/v1/orders/{orderId}` (USER)

PENDING/CONFIRMED에서 전체·부분 취소. 사유 선택(미입력 시 기본값). 차감된 재고는 복구 Saga 재사용.

**Request** (선택)
```json
{ "reason": "단순 변심" }
```

**Response** `204 No Content`

### `PATCH /api/v1/orders/{orderId}/items/{itemId}/cancel` (ADMIN/SELLER)

주문 항목 취소. ADMIN 전체 / SELLER 본인 상품 항목. 사유 필수.

**Request**
```json
{ "reason": "재고 소진" }
```

**Response** `204 No Content`

---

## 배송상태 변경

### `PATCH /api/v1/orders/{orderId}/delivery-status` (ADMIN/SELLER)

`PREPARING → SHIPPING → DELIVERED` 전진만 허용. 역행·건너뜀·동일 재설정은 `400`.

**Request**
```json
{ "status": "SHIPPING" }
```

**Response** `200 OK` — `OrderResponse`

> SHIPPING/DELIVERED 전이 시 주문 소유자에게 알림 생성.

---

## 실패 주문 조회 (M-3)

### `GET /api/v1/orders/admin/failed` (ADMIN)

재고 확보 실패 등으로 자동취소된 주문 목록. 일반화된 사유만 노출.

**Response** `200 OK` — `Page<FailedOrderResponse>`
```json
{
  "content": [
    { "orderId": 3, "userId": 5, "reason": "재고 부족", "occurredAt": "2026-07-01T10:00:00" }
  ],
  "totalElements": 1, "totalPages": 1
}
```

---

## 구매 인증 (내부 전용)

### `GET /api/v1/orders/internal/purchased?userId={u}&productId={p}`

> **X-Internal-Token 필수** — product-service 리뷰 작성 자격 판정용. 게이트웨이 화이트리스트 제외.

**Response** `200 OK`
```json
{ "purchased": true }
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | 내부 토큰 없음/불일치 |

---

## 장바구니 `/api/v1/cart`

> Redis 저장. 로그인 사용자는 `X-User-Id`, 게스트는 HttpOnly `guestId` 쿠키로 식별.

| Method | URL | 설명 | 성공 Status |
|--------|-----|------|-------------|
| GET | `/api/v1/cart` | 장바구니 조회 | `200 OK` |
| POST | `/api/v1/cart/items` | 상품 담기 (`productId`, `quantity`) | `201 Created` |
| PATCH | `/api/v1/cart/items/{productId}` | 수량 변경 (`quantity`) | `200 OK` |
| DELETE | `/api/v1/cart/items/{productId}` | 항목 삭제 | `204 No Content` |
| DELETE | `/api/v1/cart` | 장바구니 비우기 | `204 No Content` |
| POST | `/api/v1/cart/merge` | 게스트→사용자 병합 (로그인 필수, 미로그인 401) | `200 OK` |
| POST | `/api/v1/cart/guest/init` | 게스트 식별자 발급 (HttpOnly 쿠키) | `200 OK` |

---

## 배송지 주소록 `/api/v1/addresses` (USER)

| Method | URL | 설명 | 성공 Status |
|--------|-----|------|-------------|
| GET | `/api/v1/addresses` | 내 주소록 (기본 배송지 우선) | `200 OK` |
| POST | `/api/v1/addresses` | 배송지 추가 (첫 주소 자동 기본) | `201 Created` |
| PUT | `/api/v1/addresses/{addressId}` | 배송지 수정 (본인) | `200 OK` |
| DELETE | `/api/v1/addresses/{addressId}` | 배송지 삭제 (본인) | `204 No Content` |
| PATCH | `/api/v1/addresses/{addressId}/default` | 기본 배송지 지정 | `200 OK` |

**Request** (추가/수정)
```json
{ "receiver": "홍길동", "phone": "010-1234-5678", "address": "서울시 ..." }
```

> 사용자당 기본 배송지는 최대 1개 (DB 부분 유니크 인덱스로 최종 방어).

---

## 알림 `/api/v1/notifications` (USER)

| Method | URL | 설명 | 성공 Status |
|--------|-----|------|-------------|
| GET | `/api/v1/notifications/me` | 내 알림 목록 (최신순 페이징) | `200 OK` |
| GET | `/api/v1/notifications/me/unread-count` | 미읽음 개수 (뱃지) | `200 OK` |
| PATCH | `/api/v1/notifications/{id}/read` | 단건 읽음 (본인) | `204 No Content` |
| PATCH | `/api/v1/notifications/read-all` | 전체 읽음 (본인) | `204 No Content` |

**목록 응답** `200 OK` — `Page<NotificationResponse>`
```json
{
  "content": [
    { "id": 7, "type": "DELIVERY_SHIPPING", "title": "배송 시작",
      "message": "상품 배송이 시작되었습니다. (주문 #1)", "orderId": 1,
      "isRead": false, "createdAt": "2026-07-01T10:00:00" }
  ],
  "totalElements": 1, "totalPages": 1
}
```

**미읽음 개수 응답**
```json
{ "count": 3 }
```
