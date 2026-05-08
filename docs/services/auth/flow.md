# Auth Service 흐름

## JWT 구조

### 알고리즘: RS256 (비대칭키)

| 키 | 보유 주체 | 용도 |
|----|---------|------|
| 개인키 (Private Key) | Auth Service만 보유 | 토큰 서명 |
| 공개키 (Public Key) | Gateway에 배포 | 토큰 서명 검증 |

### Access Token Payload
```json
{
  "sub": "1",
  "role": "USER",
  "iat": 1700000000,
  "exp": 1700003600
}
```

---

## 로그인 흐름

```
Client → POST /api/v1/auth/login
           ↓
      AuthController
           ↓
      AuthService
        1. DB에서 User 조회 (email)
        2. BCrypt 비밀번호 검증
        3. Access Token 발급 (RS256, 1시간)
        4. Refresh Token 발급 (UUID, 7일)
        5. Redis 저장: refresh:{refreshToken} = userId (TTL 7일)
           ↓
      응답: Access Token + Refresh Token (모두 Response Body)
```

---

## 토큰 갱신 흐름 (Rotation)

```
Client → POST /api/v1/auth/refresh (Body: { "refreshToken": "..." })
           ↓
      AuthController
           ↓
      AuthService
        1. Request Body에서 Refresh Token 추출
        2. Redis 조회: refresh:{refreshToken} → userId 조회
        3. userId로 User 조회 후 신규 Access Token + 신규 Refresh Token 발급
        4. Redis 업데이트: 기존 토큰 삭제 + 신규 토큰 저장
           ↓
      응답: 신규 Access Token + 신규 Refresh Token (모두 Response Body)
```

---

## 로그아웃 흐름

```
Client → POST /api/v1/auth/logout (Body: { "refreshToken": "..." })
           ↓
      AuthService
        1. Redis에서 refresh:{refreshToken} 삭제
           ↓
      응답: 204 No Content
```

> Access Token은 블랙리스트 미사용 — 만료(1시간)까지 유효  
> 강제 무효화가 필요한 경우에만 블랙리스트 [운영]

---

## Gateway 연동 흐름

```
Client → GET /api/v1/products (Authorization: Bearer <accessToken>)
           ↓
      Gateway JwtAuthenticationFilter
        1. 화이트리스트 확인
        2. Authorization 헤더에서 토큰 추출
        3. RS256 공개키로 서명 검증
        4. Claims에서 userId, role 추출
        5. X-User-Id, X-User-Role 헤더 주입
           ↓
      Product Service (헤더로 유저 정보 수신)
```

---

## RBAC

| Role | 권한 |
|------|------|
| USER | 상품 조회, 주문 생성·조회 |
| ADMIN | 상품 CRUD, 전체 주문 조회 |

- Spring Security `@PreAuthorize("hasRole('ADMIN')")` 로 엔드포인트 보호
- Gateway는 토큰 검증만, 세부 권한 확인은 각 서비스에서 처리
