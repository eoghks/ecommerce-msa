# API 명세

> Base URL: `http://localhost:8080` (API Gateway)
> 응답 포맷: HTTP Status Code 기반, 에러는 RFC 7807 `ProblemDetail`

---

## Auth Service `/api/v1/auth`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/auth/signup` | 회원가입 | 불필요 | `201 Created` |
| POST | `/api/v1/auth/login` | 로그인 (Access 바디 + Refresh HttpOnly 쿠키) | 불필요 | `200 OK` |
| POST | `/api/v1/auth/refresh` | Access Token 재발급 (Refresh 쿠키 회전) | 불필요 | `200 OK` |
| GET | `/api/v1/auth/check-email` | 이메일 중복 확인 | 불필요 | `200 OK` |
| POST | `/api/v1/auth/forgot-password` | 임시 비밀번호 발급 | 불필요 | `200 OK` |
| GET | `/api/v1/auth/.well-known/jwks.json` | JWKS 공개키 조회 | 불필요 | `200 OK` |
| POST | `/api/v1/auth/logout` | 로그아웃 (Refresh 무효화 + 쿠키 만료) | JWT 필요 | `204 No Content` |
| GET | `/api/v1/auth/me` | 내 정보 조회 | JWT 필요 | `200 OK` |
| POST | `/api/v1/auth/change-password` | 비밀번호 변경 | JWT 필요 | `204 No Content` |
| POST | `/api/v1/auth/seller/apply` | 판매자 신청 + mock 인증 → SELLER 승격 | JWT 필요 | `200 OK` |
| GET | `/api/v1/auth/users?ids=` | 서비스 간 사용자 요약 배치 조회 | 내부 전용 (X-Internal-Token) | `200 OK` |

> `/api/v1/auth/users`는 게이트웨이 화이트리스트에서 제외된 서비스 간 직접 호출 전용 엔드포인트다.

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 (이메일 형식, 비밀번호 규칙 — 8자+대소문자+숫자+특수문자) |
| `401` | 로그인 실패 / 토큰 만료 / 현재 비밀번호 불일치 / 내부 토큰 불일치 |
| `409` | 이메일 중복 |

---

## Product Service `/api/v1/products`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/products` | 상품 목록 (검색·정렬·가격필터) | 불필요 | `200 OK` |
| GET | `/api/v1/products/{id}` | 상품 상세 (캐싱, 평균별점 포함) | 불필요 | `200 OK` |
| GET | `/api/v1/products/suggestions` | 상품명 자동완성 (판매중 한정) | 불필요 | `200 OK` |
| GET | `/api/v1/products/mine` | 내 상품 목록 | SELLER | `200 OK` |
| POST | `/api/v1/products` | 상품 등록 | ADMIN/SELLER | `201 Created` |
| PUT | `/api/v1/products/{id}` | 상품 수정 (ADMIN 전체 / SELLER 본인) | ADMIN/SELLER | `200 OK` |
| DELETE | `/api/v1/products/{id}` | 상품 삭제 (ADMIN 전체 / SELLER 본인) | ADMIN/SELLER | `204 No Content` |
| PATCH | `/api/v1/products/{id}/ban` | 판매 금지 | ADMIN | `204 No Content` |
| PATCH | `/api/v1/products/{id}/unban` | 판매 금지 해제 | ADMIN | `204 No Content` |
| POST | `/api/v1/products/upload-image` | 상품 이미지 업로드(MinIO) | ADMIN/SELLER | `200 OK` |

**목록 조회 쿼리 파라미터**: `categoryId`, `keyword`, `minPrice`, `maxPrice`, `sort`(`latest`/`price_asc`/`price_desc`/`name` 화이트리스트, 미허용값은 `latest` 폴백), `includeBanned`(ADMIN 전용), `page`, `size`.
**자동완성**: `keyword`(trim 후 1자 이상), `limit`(기본 10, 최대 20).

### 카테고리 `/api/v1/categories`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/categories` | 카테고리 목록 | 불필요 | `200 OK` |
| POST | `/api/v1/categories` | 카테고리 등록 | ADMIN | `201 Created` |
| PUT | `/api/v1/categories/{id}` | 카테고리 수정 | ADMIN | `200 OK` |
| DELETE | `/api/v1/categories/{id}` | 카테고리 삭제 | ADMIN | `204 No Content` |

### 리뷰·별점 `/api/v1/products/{productId}/reviews`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/products/{productId}/reviews` | 리뷰 목록 (최신순 페이징) | 불필요 | `200 OK` |
| POST | `/api/v1/products/{productId}/reviews` | 리뷰 작성 (구매자·1인1리뷰) | USER | `201 Created` |
| PUT | `/api/v1/products/{productId}/reviews/{reviewId}` | 리뷰 수정 (본인) | USER | `200 OK` |
| DELETE | `/api/v1/products/{productId}/reviews/{reviewId}` | 리뷰 삭제 (본인/ADMIN) | USER | `204 No Content` |

> 작성 시 order-service 내부 호출(`/orders/internal/purchased`)로 구매 인증. 리뷰 변경 시 평균 별점 재계산 + 상세 캐시 무효화.

### 위시리스트(찜) `/api/v1/wishlist`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/wishlist/{productId}` | 찜 추가 (멱등) | USER | `204 No Content` |
| DELETE | `/api/v1/wishlist/{productId}` | 찜 해제 (멱등) | USER | `204 No Content` |
| GET | `/api/v1/wishlist/me` | 내 찜 목록 (페이징) | USER | `200 OK` |
| GET | `/api/v1/wishlist/me/ids` | 내 찜 상품 ID 집합 (하트 표시용) | USER | `200 OK` |

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 (가격 범위 역전/음수 등) |
| `401` | 인증 토큰 없음 / 미구매(리뷰) |
| `403` | 권한 없음 (타 판매자 상품, ADMIN 전용 등) |
| `404` | 상품/카테고리/리뷰 없음 |
| `409` | 리뷰 중복 (1인1리뷰) |

---

## Order Service `/api/v1/orders`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/orders` | 주문 생성 (배송지 addressId 또는 직접입력) | USER | `201 Created` |
| GET | `/api/v1/orders/me` | 내 주문 목록 (페이징) | USER | `200 OK` |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 | USER | `200 OK` |
| DELETE | `/api/v1/orders/{orderId}` | 주문 취소 (전체/부분, 사유 선택) | USER | `204 No Content` |
| PATCH | `/api/v1/orders/{orderId}/items/{itemId}/cancel` | 주문 항목 취소 (사유 필수) | ADMIN/SELLER | `204 No Content` |
| PATCH | `/api/v1/orders/{orderId}/delivery-status` | 배송상태 변경 (전진만) | ADMIN/SELLER | `200 OK` |
| GET | `/api/v1/orders/admin` | 전체 주문 목록 | ADMIN | `200 OK` |
| GET | `/api/v1/orders/admin/failed` | 실패(자동취소) 주문 목록 | ADMIN | `200 OK` |
| GET | `/api/v1/orders/seller` | 판매자 주문 목록 (본인 항목만) | SELLER | `200 OK` |
| GET | `/api/v1/orders/internal/purchased` | 구매 인증 (리뷰 자격) | 내부 전용 (X-Internal-Token) | `200 OK` |

> 배송상태(`delivery-status`)는 `PREPARING → SHIPPING → DELIVERED` 전진만 허용. 주문상태(status)와는 별도 축.
> 배송상태 전이/주문 확정·취소 시 주문 소유자에게 인앱 알림 생성.

### 장바구니 `/api/v1/cart`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/cart` | 장바구니 조회 | USER/게스트 | `200 OK` |
| POST | `/api/v1/cart/items` | 상품 담기 | USER/게스트 | `201 Created` |
| PATCH | `/api/v1/cart/items/{productId}` | 수량 변경 | USER/게스트 | `200 OK` |
| DELETE | `/api/v1/cart/items/{productId}` | 항목 삭제 | USER/게스트 | `204 No Content` |
| DELETE | `/api/v1/cart` | 장바구니 비우기 | USER/게스트 | `204 No Content` |
| POST | `/api/v1/cart/merge` | 게스트→사용자 장바구니 병합 | USER | `200 OK` |
| POST | `/api/v1/cart/guest/init` | 게스트 식별자 발급 (HttpOnly 쿠키) | 불필요 | `200 OK` |

### 배송지 주소록 `/api/v1/addresses`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/addresses` | 내 주소록 (기본 배송지 우선) | USER | `200 OK` |
| POST | `/api/v1/addresses` | 배송지 추가 (첫 주소는 자동 기본) | USER | `201 Created` |
| PUT | `/api/v1/addresses/{addressId}` | 배송지 수정 (본인) | USER | `200 OK` |
| DELETE | `/api/v1/addresses/{addressId}` | 배송지 삭제 (본인) | USER | `204 No Content` |
| PATCH | `/api/v1/addresses/{addressId}/default` | 기본 배송지 지정 | USER | `200 OK` |

### 알림 `/api/v1/notifications`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/notifications/me` | 내 알림 목록 (최신순 페이징) | USER | `200 OK` |
| GET | `/api/v1/notifications/me/unread-count` | 미읽음 개수 (뱃지) | USER | `200 OK` |
| PATCH | `/api/v1/notifications/{id}/read` | 단건 읽음 (본인) | USER | `204 No Content` |
| PATCH | `/api/v1/notifications/read-all` | 전체 읽음 (본인) | USER | `204 No Content` |

### 반품·환불 (V1.1-5)

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/orders/{orderId}/items/{itemId}/returns` | 반품 신청 (주문 소유자, 사유 필수) | USER | `201 Created` |
| GET | `/api/v1/returns/me` | 내 반품 목록 (최신순 페이징) | USER | `200 OK` |
| GET | `/api/v1/returns/admin` | 반품 관리 목록 (ADMIN 전체 / SELLER 본인 상품 건만) | ADMIN/SELLER | `200 OK` |
| PATCH | `/api/v1/returns/{returnId}/approve` | 반품 승인 (재고 복구 + 환불 처리) | ADMIN/SELLER | `200 OK` |
| PATCH | `/api/v1/returns/{returnId}/reject` | 반품 거부 (거부 사유 필수) | ADMIN/SELLER | `200 OK` |

**요청 바디**: 신청 `{ "reason": "..." }` / 거부 `{ "rejectReason": "..." }` — 둘 다 필수, 300자 이하.
**반품 상태**: `REQUESTED → APPROVED → REFUNDED`, `REQUESTED → REJECTED`(거부 건은 재신청 허용).

> 신청 자격은 배송완료(`DELIVERED`) 주문의 활성(ACTIVE) 항목이며, 항목당 진행 중(REQUESTED·APPROVED·REFUNDED) 반품은 1건만 허용한다(DB 부분 유니크로 최종 방어).
> 승인 시 기존 항목취소 경로를 재사용해 재고를 복구하고 환불 처리(mock) 후 `REFUNDED`로 전이한다. 승인·거부·환불 시 신청자에게 인앱 알림 생성.

### 관리자 매출 통계 `/api/v1/admin/stats` (V1.1-7)

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/admin/stats/summary` | 기간 요약 (매출·주문수·AOV·취소율·실패주문) | ADMIN | `200 OK` |
| GET | `/api/v1/admin/stats/daily` | 일별 매출 추이 (빈 날짜 0 채움) | ADMIN | `200 OK` |
| GET | `/api/v1/admin/stats/products` | 상품별 매출 Top N | ADMIN | `200 OK` |
| GET | `/api/v1/admin/stats/sellers` | 판매자별 매출 Top N | ADMIN | `200 OK` |
| GET | `/api/v1/admin/stats/failed-orders` | 실패(자동취소) 주문 일별 추이 | ADMIN | `200 OK` |

**공통 파라미터**: `from`, `to` (ISO date `yyyy-MM-dd`, `to` 포함). 미지정 시 오늘 포함 최근 30일. `from > to` 또는 366일 초과는 `400`.
**Top N 파라미터**: `limit` (`products`/`sellers` 전용, 기본 10 · 1~50 범위 밖은 `400`).

> 유효 매출 = 집계 대상 주문(`CONFIRMED`/`PARTIALLY_CANCELLED`)의 ACTIVE 항목 `price × quantity` 합계 — 부분취소가 반영되지 않는 `total_price`는 쓰지 않는다.
> 취소율 = (전체취소 + 부분취소) / `PENDING` 제외 전체 주문수. 날짜 집계 기준 타임존은 KST(`Asia/Seoul`) 고정.

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 / 배송상태 역행·건너뜀 / 반품 자격 미충족(배송완료 아님·취소된 항목) / 통계 기간·limit 범위 초과 |
| `401` | 인증 토큰 없음 / 내부 토큰 불일치 |
| `403` | 본인 주문 아님 / 권한 없음 (반품 대상이 본인 상품이 아닌 판매자 등) |
| `404` | 주문/주소/알림/반품 없음 (타인 소유 리소스 포함 — 정보 노출 방지) |
| `409` | 재고 부족 / 취소 불가 상태 (이미 배송됨 등) / 반품 중복 신청 / 반품 상태 충돌 (이미 처리된 건 재승인·재거부, 동시 처리 패자) |

---

## 응답 포맷

### 공통 요청 오류 정책 (F-01 / F-05)

요청 자체가 해석되지 않는 오류는 전 서비스 공통(`common` 모듈 `GlobalExceptionHandler`)으로 아래와 같이 매핑한다.

| Status | 상황 |
|--------|------|
| `400` | 요청 본문 해석 실패(깨진 JSON·알 수 없는 enum 값) / 파라미터 타입 불일치 / 필수 파라미터·multipart 파트 누락 |
| `404` | 매핑된 핸들러가 없는 경로 |
| `413` | 업로드 용량 상한 초과 |

> 응답·로그에 요청 본문·요청 경로 원문을 그대로 노출하지 않고 일반화된 메시지만 사용한다.

### 성공 응답
HTTP Status Code 로 상태 표현, 응답 바디는 리소스만 포함.

**`200 OK` — 조회/수정**
```json
{
  "id": 1,
  "name": "상품A",
  "price": 10000
}
```

**`201 Created` — 생성**
```
HTTP/1.1 201 Created
Location: /api/v1/products/1

{
  "id": 1,
  "name": "상품A"
}
```

**`204 No Content` — 삭제**
```
HTTP/1.1 204 No Content
(응답 바디 없음)
```

### 에러 응답 — RFC 7807 ProblemDetail

```
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://api.eoghks.com/errors/stock-insufficient",
  "title": "재고 부족",
  "status": 409,
  "detail": "상품 ID 5의 재고가 부족합니다",
  "instance": "/api/v1/orders"
}
```

### 입력값 검증 실패 응답
```
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값 검증 실패",
  "instance": "/api/v1/auth/signup",
  "errors": [
    { "field": "email", "message": "이메일 형식이 올바르지 않습니다" },
    { "field": "password", "message": "비밀번호는 8자 이상이어야 합니다" }
  ]
}
```
