# API 명세

> Base URL: `http://localhost:8080` (API Gateway)

## Auth Service `/api/auth`

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/auth/signup` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | 불필요 |
| POST | `/api/auth/logout` | 로그아웃 (토큰 블랙리스트) | 필요 |
| GET | `/api/auth/me` | 내 정보 조회 | 필요 |

## Product Service `/api/products`

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| GET | `/api/products` | 상품 목록 (캐싱) | 불필요 |
| GET | `/api/products/{id}` | 상품 상세 | 불필요 |
| POST | `/api/products` | 상품 등록 | ADMIN |
| PUT | `/api/products/{id}` | 상품 수정 | ADMIN |
| DELETE | `/api/products/{id}` | 상품 삭제 | ADMIN |

## Order Service `/api/orders`

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/orders` | 주문 생성 | USER |
| GET | `/api/orders` | 내 주문 목록 | USER |
| GET | `/api/orders/{id}` | 주문 상세 | USER |
| PATCH | `/api/orders/{id}/cancel` | 주문 취소 | USER |

## 공통 응답 포맷

```json
{
  "success": true,
  "data": { },
  "message": "요청 처리 완료"
}
```

## 에러 응답 포맷

```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```
