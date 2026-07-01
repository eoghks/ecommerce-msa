# Auth Service API

Base URL: `/api/v1/auth`  
Gateway 화이트리스트 (JWT 불필요): `/login`, `/signup`, `/refresh`, `/check-email`, `/.well-known/jwks.json`  
JWT 필요: `/logout`, `/me`, `/change-password`

---

## 회원가입

### `POST /api/v1/auth/signup`

**Request**
```json
{
  "email": "user@example.com",
  "password": "Admin1234!",
  "name": "홍길동"
}
```

> 비밀번호 규칙: 8자 이상, 대문자·소문자·숫자·특수문자 각 1자 이상 포함

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
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```
```
Set-Cookie: refreshToken=550e8400-...; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax
```

> `expiresIn` 단위: 밀리초 (3600000 = 1시간)  
> **Refresh Token은 HttpOnly 쿠키로 전달** — 응답 바디에는 포함하지 않음 (XSS 방어)  
> Access Token은 바디로 반환 → 클라이언트 메모리에만 보관 (localStorage 미사용)  
> `Secure` 플래그는 운영(HTTPS)에서만 활성화 (`app.cookie.secure`)

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | 이메일 또는 비밀번호 불일치 |

---

## 토큰 갱신

### `POST /api/v1/auth/refresh`

> **Request Body 없음** — Refresh Token은 HttpOnly 쿠키에서 자동 전송  
> 새 탭/새로고침 시 클라이언트가 이 엔드포인트로 세션 복원

**Request**
```
Cookie: refreshToken=550e8400-...   (브라우저 자동 전송)
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```
```
Set-Cookie: refreshToken=새로운-uuid; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax
```

> Refresh Token Rotation: 기존 토큰 Redis에서 삭제 후 신규 토큰 발급 + 쿠키 재설정

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | Refresh 쿠키 없음 / 만료 / Redis에 없음 |

---

## 로그아웃

### `POST /api/v1/auth/logout`

> **JWT 필요** — `Authorization: Bearer <accessToken>` 헤더 전송  
> Gateway가 X-User-Id 주입 → 해당 유저의 모든 Refresh Token Redis에서 삭제

**Request Body** 없음

**Response** `204 No Content`
```
Set-Cookie: refreshToken=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax
```

> Refresh Token 쿠키도 즉시 만료 처리

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | Access Token 없음 / 만료 |

---

## 내 정보 조회

### `GET /api/v1/auth/me`

> **JWT 필요** — Gateway가 X-User-Id 헤더 주입

**Response** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER",
  "createdAt": "2026-05-26T10:00:00"
}
```

---

## 비밀번호 변경

### `POST /api/v1/auth/change-password`

> **JWT 필요**  
> 변경 성공 시 해당 유저의 모든 Refresh Token Redis에서 삭제 (자동 로그아웃 처리)

**Request**
```json
{
  "email": "user@example.com",
  "currentPassword": "Admin1234!",
  "newPassword": "NewPass5678@"
}
```

**Response** `204 No Content`

**Error**
| 상태코드 | 사유 |
|---------|------|
| 400 | 새 비밀번호 규칙 미충족 |
| 401 | 현재 비밀번호 불일치 |

---

## 이메일 중복 확인

### `GET /api/v1/auth/check-email?email={email}`

**Response** `200 OK`
```json
{ "available": true }
```

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
