# ADR-002 · JWT 설계

> **상태**: ✅ 확정 — Week 2 Auth Service 구현 완료

---

## 배경

MSA 환경에서 여러 서비스가 독립적으로 사용자 인증을 처리해야 한다.
API Gateway가 모든 요청의 진입점이 되므로, Gateway에서 토큰을 검증하고
인증된 사용자 정보를 헤더로 전달하는 구조가 필요하다.

**Session vs JWT 비교:**

| 항목 | Session | JWT |
|------|---------|-----|
| 상태 관리 | 서버(Redis 등) | 클라이언트 |
| MSA 확장성 | 공유 세션 저장소 필요 | 무상태 — 각 서비스 독립 검증 가능 |
| 네트워크 | 매 요청마다 세션 조회 | 토큰 자체에 클레임 포함 |

MSA에서 각 서비스가 공유 세션 저장소에 의존하면 결합도가 높아진다.
JWT는 무상태(Stateless)로 Gateway가 토큰만으로 검증 가능하므로 선택.

---

## 후보 비교

| 항목 | HS256 (대칭) | RS256 (비대칭) | ES256 (타원곡선) |
|------|------------|--------------|----------------|
| 키 종류 | 단일 Secret Key | Private Key + Public Key | Private Key + Public Key |
| MSA 검증 분산 | Secret Key를 모든 서비스가 알아야 함 ❌ | Public Key만 배포하면 됨 ✅ | Public Key만 배포하면 됨 ✅ |
| 서명 성능 | 빠름 | RS256보다 느림 | RS256보다 빠름 |
| 키 크기 | 작음 | 2048bit (큼) | 256bit (작음) |
| 라이브러리 지원 | 광범위 | 광범위 | 상대적으로 적음 |

---

## 선택: RS256

**선택 근거:**

MSA 핵심 요구사항은 **검증 주체 분리**다.
- HS256: Secret Key가 유출되면 모든 서비스가 위험 — Gateway뿐 아니라 Product/Order 서비스도 키를 알아야 함
- RS256: **Auth Service만 Private Key 보유**, Gateway·각 서비스는 Public Key(JWKS)만으로 검증 가능
- ES256: 보안·성능 모두 우수하나 라이브러리 생태계가 RS256보다 작음. 포트폴리오 단계에서 RS256으로 충분

**토큰 구조 (Claims):**

```json
{
  "sub": "1",
  "role": "USER",
  "iat": 1700000000,
  "exp": 1700003600
}
```

| Claim | 설명 |
|-------|------|
| `sub` | userId (DB PK) |
| `role` | USER \| ADMIN (RBAC) |
| `iat` | 발급 시각 |
| `exp` | 만료 시각 |

**만료 정책:**

| 토큰 | TTL | 이유 |
|------|-----|------|
| Access Token | 1시간 | 짧은 생명주기로 탈취 피해 최소화 |
| Refresh Token | 7일 | 사용자 편의 — 매일 로그인 불필요 |

---

## 적용 방식

### Auth Service — 토큰 발급
```
로그인 성공
→ RSA Private Key로 RS256 서명 → Access Token
→ UUID 생성 → Refresh Token
→ Redis 저장: refresh:{refreshToken} = userId (TTL 7일)
→ 두 토큰 모두 Response Body로 반환
```

### Gateway — 토큰 검증
```
요청 수신
→ JWKS 엔드포인트(auth-service)에서 RSA Public Key fetch (5분 주기 캐싱)
→ RS256 서명 검증 + exp 체크
→ X-User-Id, X-User-Role 헤더 주입 → 다운스트림 서비스 전달
```

### Refresh Token 저장 전략
- Redis Key: `refresh:{refreshToken}` → Value: `userId`
- 역방향 조회(token → userId): 갱신 시 DB 조회 없이 userId 획득
- Rotation: 갱신할 때마다 기존 토큰 삭제 + 신규 토큰 저장

### 로그아웃
- Redis에서 `refresh:{refreshToken}` 삭제 → Refresh Token 무효화
- Access Token은 별도 블랙리스트 없음 — 만료(1시간)까지 유효
- 보안 사고 시 강제 무효화: 블랙리스트 [운영]

---

## 한계 및 향후 개선

| 한계 | 현재 | 운영 수준 해결책 |
|------|------|----------------|
| RSA 키 기동 시마다 새로 생성 | @PostConstruct 인메모리 생성 | Vault/K8s Secret에서 고정 PEM 주입 (CR-01) |
| Refresh Rotation TOCTOU | 비원자적 조회-삭제-저장 | Redis Lua 스크립트 원자적 처리 (CR-02) |
| Access Token 강제 무효화 불가 | 만료 전 탈취 시 1시간 유효 | Redis 블랙리스트 (exp까지 TTL) |

---

## 면접 답변 시나리오

> **Q: 왜 JWT를 선택했나요?**  
> A: MSA 환경에서 각 서비스가 독립적으로 사용자를 인증하려면 무상태 토큰이 적합합니다. Session 방식은 공유 저장소가 필요해 서비스 간 결합도가 높아지지만, JWT는 Gateway에서 토큰만으로 검증 후 사용자 정보를 헤더로 전달하면 각 서비스가 별도 인증 없이 처리할 수 있습니다.

> **Q: RS256과 HS256 차이는?**  
> A: HS256은 단일 Secret Key로 서명과 검증을 모두 합니다. MSA에서는 검증이 필요한 모든 서비스가 Secret Key를 알아야 하는데, 키 유출 시 전체가 위험합니다. RS256은 Private Key로 서명하고 Public Key로만 검증하므로, Auth Service만 Private Key를 보유하고 나머지 서비스는 Public Key(JWKS)만으로 안전하게 검증할 수 있습니다.

> **Q: 토큰 탈취 대응은?**  
> A: Access Token은 TTL을 1시간으로 짧게 유지해 탈취 피해를 최소화합니다. Refresh Token은 Rotation을 적용해 사용할 때마다 새 토큰으로 교체되므로, 탈취된 토큰을 먼저 사용하면 정상 사용자의 갱신 요청이 실패해 재로그인으로 감지할 수 있습니다. 운영 환경에서는 추가로 Access Token 블랙리스트를 도입할 수 있습니다.
