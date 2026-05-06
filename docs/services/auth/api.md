# Auth Service API

Base URL: `/api/v1/auth`  
인증 불필요 경로 (Gateway 화이트리스트): `/api/v1/auth/**`

---

## 회원가입

### `POST /api/v1/auth/register`

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
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```
- Refresh Token은 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | 이메일 또는 비밀번호 불일치 |

---

## 토큰 갱신

### `POST /api/v1/auth/refresh`

**Request**
- Body 없음
- Cookie: `refreshToken=<token>` (자동 전송)

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```
- 신규 Refresh Token `Set-Cookie` 재발급 (Rotation)

**Error**
| 상태코드 | 사유 |
|---------|------|
| 401 | Refresh Token 없음 / 만료 / Redis에 없음 |

---

## 로그아웃

### `POST /api/v1/auth/logout`

**Request**
- Header: `Authorization: Bearer <accessToken>`
- Cookie: `refreshToken=<token>`

**Response** `204 No Content`
- Redis에서 Refresh Token 삭제
- `Set-Cookie: refreshToken=; Max-Age=0` (쿠키 즉시 만료)

---

## 내 정보 조회

### `GET /api/v1/auth/me`

**Request**
- Header: `Authorization: Bearer <accessToken>`
- Gateway가 `X-User-Id`, `X-User-Role` 헤더 주입

**Response** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER"
}
```
