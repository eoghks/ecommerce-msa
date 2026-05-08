# Auth Service Overview

## 역할
회원가입, 로그인, JWT 토큰 발급·갱신·무효화, RBAC 권한 관리를 담당하는 인증/인가 서비스.

## 기본 정보

| 항목 | 값 |
|------|----|
| 포트 | 8081 |
| DB | auth_db (PostgreSQL 16, Docker port 5433) |
| Redis | Refresh Token 저장, TTL 7일 |
| 의존 서비스 | 없음 (다른 서비스들이 Gateway를 통해 이 서비스에 의존) |

## 주요 기술

- **JWT RS256**: RSA 비대칭키 서명 — 서비스 기동 시 2048bit 키페어 생성 (`@PostConstruct`)
- **Access Token**: 1시간(3600000ms), Response Body로 반환
- **Refresh Token**: 7일, UUID, Response Body로 반환, Redis 저장 + Rotation
- **RBAC**: USER / ADMIN 두 가지 역할
- **BCrypt**: 비밀번호 해싱 (Spring Security `PasswordEncoder`)
- **Flyway**: DB 마이그레이션 (`V1__init_schema.sql`)

## Redis 저장 구조

| Key | Value | TTL |
|-----|-------|-----|
| `refresh:{refreshToken}` | `{userId}` (String) | 7일 |

## 구현 현황 (Day 1~4)

- [x] User 엔티티 + JPA 설정 (JPA Auditing, `@CreatedDate`/`@LastModifiedDate`)
- [x] Flyway 마이그레이션 (`V1__init_schema.sql`)
- [x] 회원가입 API (`POST /api/v1/auth/signup`)
- [x] 로그인 API (`POST /api/v1/auth/login`)
- [x] 토큰 갱신 API (`POST /api/v1/auth/refresh`) — Rotation 적용
- [x] 로그아웃 API (`POST /api/v1/auth/logout`)
- [x] RSA 키 생성 + JwtProvider 구현 (nimbus-jose-jwt)
- [x] Redis Refresh Token 저장·검증·무효화 (`RefreshTokenRepository`)
- [x] Spring Security 설정 (STATELESS, CSRF disable)
- [x] Gateway JWT 화이트리스트 필터 (`JwtAuthenticationFilter`)
- [x] Gateway JWT 서명 검증 완성 (Week 2 Day 5)
- [ ] `/api/v1/auth/me` 내 정보 조회 API
