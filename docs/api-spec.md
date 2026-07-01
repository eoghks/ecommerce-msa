# API 명세

> Base URL: `http://localhost:8080` (API Gateway)
> 응답 포맷: HTTP Status Code 기반, 에러는 RFC 7807 `ProblemDetail`

---

## Auth Service `/api/v1/auth`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/auth/signup` | 회원가입 | 불필요 | `201 Created` |
| POST | `/api/v1/auth/login` | 로그인 (JWT 발급) | 불필요 | `200 OK` |
| POST | `/api/v1/auth/refresh` | Access Token 재발급 | 불필요 | `200 OK` |
| GET | `/api/v1/auth/check-email` | 이메일 중복 확인 | 불필요 | `200 OK` |
| POST | `/api/v1/auth/logout` | 로그아웃 (Redis 전체 삭제) | JWT 필요 | `204 No Content` |
| GET | `/api/v1/auth/me` | 내 정보 조회 | JWT 필요 | `200 OK` |
| POST | `/api/v1/auth/change-password` | 비밀번호 변경 | JWT 필요 | `204 No Content` |

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 (이메일 형식, 비밀번호 규칙 — 8자+대소문자+숫자+특수문자) |
| `401` | 로그인 실패 / 토큰 만료 / 현재 비밀번호 불일치 |
| `409` | 이메일 중복 |

---

## Product Service `/api/v1/products`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| GET | `/api/v1/products` | 상품 목록 (캐싱) | 불필요 | `200 OK` |
| GET | `/api/v1/products/{id}` | 상품 상세 | 불필요 | `200 OK` |
| POST | `/api/v1/products` | 상품 등록 | ADMIN | `201 Created` |
| PATCH | `/api/v1/products/{id}` | 상품 부분 수정 | ADMIN | `200 OK` |
| DELETE | `/api/v1/products/{id}` | 상품 삭제 | ADMIN | `204 No Content` |

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 |
| `401` | 인증 토큰 없음 |
| `403` | ADMIN 권한 없음 |
| `404` | 상품 없음 |

---

## Order Service `/api/v1/orders`

| Method | URL | 설명 | 인증 | 성공 Status |
|--------|-----|------|------|-------------|
| POST | `/api/v1/orders` | 주문 생성 | USER | `201 Created` |
| GET | `/api/v1/orders` | 내 주문 목록 | USER | `200 OK` |
| GET | `/api/v1/orders/{id}` | 주문 상세 | USER | `200 OK` |
| PATCH | `/api/v1/orders/{id}/cancel` | 주문 취소 | USER | `200 OK` |

### 주요 에러
| Status | 상황 |
|--------|------|
| `400` | 입력값 검증 실패 |
| `401` | 인증 토큰 없음 |
| `403` | 본인 주문 아님 |
| `404` | 주문 없음 |
| `409` | 재고 부족 |
| `422` | 취소 불가 상태 (이미 배송됨 등) |

---

## 응답 포맷

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
