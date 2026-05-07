# Auth Service API

Base URL: `/api/v1/auth`  
인증 불필요 경로 (Gateway 화이트리스트): `/login`, `/signup`, `/refresh`, `/logout`

---

## 회원가입

### `POST /api/v1/auth/signup`

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123!",
  "name": "홍길동"
}
```

**Response** `201 Created`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER"
}
```

**Error**
| 상태코드 | 사유 |
|---------|------|
| 400 | 입력값 검증 실패 (email 형식, password 길이 등) |
| 409 | 이미 가입된 이메일 |

---

## 로그인

### `POST /api/v1/auth/login`

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

> `expiresIn` 단위: 밀리초 (3600000 = 1시간)  
> Refresh Token은 Response Body로 반환 (쿠키 미사용)

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | 이메일 또는 비밀번호 불일치 |

---

## 토큰 갱신

### `POST /api/v1/auth/refresh`

**Request**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "새로운-uuid",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

> Refresh Token Rotation: 요청 시 기존 토큰 Redis에서 삭제 후 신규 토큰 발급

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | Refresh Token 없음 / 만료 / Redis에 없음 |

---

## 로그아웃

### `POST /api/v1/auth/logout`

**Request**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** `204 No Content`

> Redis에서 Refresh Token 삭제

---

## JWT 구조

| 항목 | 값 |
|------|-----|
| 알고리즘 | RS256 (RSA 비대칭키) |
| Access Token 유효시간 | 1시간 (3600000ms) |
| Refresh Token 유효시간 | 7일 |
| 키 생성 시점 | 서비스 기동 시 `@PostConstruct` (메모리) |

**Claims**
```json
{
  "sub": "1",
  "role": "USER",
  "iat": 1234567890,
  "exp": 1234571490
}
```
