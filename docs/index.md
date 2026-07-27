# 기능 → 파일 크로스 레퍼런스

기능 구현 시 이 파일에 행을 추가합니다.  
추가 규칙: [docs-rule.md](../rules/docs-rule.md)

---

## 설계 문서 (V1.1)

| 문서 | 내용 |
|------|------|
| [design/wishlist.md](design/wishlist.md) | 위시리스트(찜) 설계 (V1.1-2) |
| [design/search-enhancement.md](design/search-enhancement.md) | 상품 검색 고도화 — 정렬·가격필터·자동완성 (V1.1-8) |
| [design/review-rating.md](design/review-rating.md) | 상품 리뷰·별점 (V1.1-1) |
| [design/delivery-address.md](design/delivery-address.md) | 배송지 관리 + 배송 상태 (V1.1-3) |
| [design/notification.md](design/notification.md) | 알림(주문·배송) (V1.1-4) |

## 운영

| 문서 | 내용 |
|------|------|
| [deploy-checklist.md](deploy-checklist.md) | 운영 배포 전 보안·설정 체크리스트 |

---

## 인프라 / 공통

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| Docker Compose 인프라 | `docker/docker-compose.yml` | — | ops-rule.md |
| DB 초기화 스크립트 | `docker/postgres/init.sql` | — | — |
| Spring Cloud Gateway | `gateway/src/.../GatewayApplication.java` | — | security-rule.md |
| JWT 인증 필터 (스텁) | `gateway/src/.../JwtAuthenticationFilter.java` | `docs/services/auth/flow.md` | security-rule.md |
| CORS 설정 | `gateway/src/.../CorsConfig.java` | — | security-rule.md |
| ProblemDetail 핸들러 | `common/src/.../GlobalExceptionHandler.java` | — | coding-rule.md |
| MDC 로깅 필터 | `common/src/.../MdcLoggingFilter.java` | — | ops-rule.md |
| Kafka 이벤트 베이스 | `common/src/.../BaseEvent.java` | — | coding-rule.md |
| GitHub Actions CI | `.github/workflows/ci.yml` | — | ops-rule.md |

---

## Auth Service

> 문서 위치: `docs/services/auth/`  
> 구현 완료: Week 2 ✅

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| 회원가입 | `auth-service/src/.../service/AuthService.java` | `docs/services/auth/api.md` | security-rule.md |
| 로그인 + JWT 발급 | `auth-service/src/.../service/AuthService.java` | `docs/services/auth/flow.md` | security-rule.md |
| Refresh Token 갱신 (Rotation) | `auth-service/src/.../service/AuthService.java` | `docs/services/auth/flow.md` | security-rule.md |
| 로그아웃 (Refresh 무효화) | `auth-service/src/.../service/AuthService.java` | `docs/services/auth/flow.md` | security-rule.md |
| RSA 키 생성 + JWT RS256 서명 | `auth-service/src/.../jwt/JwtProvider.java` | `docs/decisions/ADR-002-jwt-design.md` | security-rule.md |
| JWKS 엔드포인트 | `auth-service/src/.../controller/AuthController.java` | `docs/services/auth/api.md` | security-rule.md |
| Redis Refresh Token 저장 | `auth-service/src/.../repository/RefreshTokenRepository.java` | `docs/services/auth/entity.md` | security-rule.md |
| ADMIN 시드 계정 초기화 | `auth-service/src/.../config/DataInitializer.java` | `docs/tradeoffs/LW-04-role-management.md` | security-rule.md |
| Gateway 공개키 fetch·캐싱 | `gateway/src/.../client/JwksClient.java` | `docs/services/auth/flow.md` | security-rule.md |
| Gateway JWT 서명 검증 + 헤더 주입 | `gateway/src/.../filter/JwtAuthenticationFilter.java` | `docs/services/auth/flow.md` | security-rule.md |
| Gateway SecurityContext 등록 | `gateway/src/.../filter/JwtAuthenticationFilter.java` | `docs/decisions/ADR-002-jwt-design.md` | security-rule.md |

---

## Product Service

> 문서 위치: `docs/services/product/`  
> 구현 예정: Week 3

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| (Week 3 구현 후 추가) | | | |

---

## Order Service

> 문서 위치: `docs/services/order/`  
> 구현 예정: Week 4

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| (Week 4 구현 후 추가) | | | |

---

## Monitoring Service

> 문서 위치: `docs/services/monitoring/`  
> 구현 예정: Week 6

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| (Week 6 구현 후 추가) | | | |

---

## Frontend

> 구현 예정: Week 5

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| (Week 5 구현 후 추가) | | | |
