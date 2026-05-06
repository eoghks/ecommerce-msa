# Auth Service Overview

## 역할
회원가입, 로그인, JWT 토큰 발급·갱신·무효화, RBAC 권한 관리를 담당하는 인증/인가 서비스.

## 기본 정보

| 항목 | 값 |
|------|----|
| 포트 | 8081 |
| DB | auth_db (PostgreSQL) |
| Redis | Refresh Token 저장, TTL 관리 |
| Kafka | UserRegisteredEvent 발행 |
| 의존 서비스 | 없음 (다른 서비스들이 Gateway를 통해 이 서비스에 의존) |

## 주요 기술

- **JWT RS256**: 비대칭키 서명 — Gateway가 공개키로 검증, Auth만 개인키 보유
- **Access Token**: 30분, 메모리(Zustand) 저장
- **Refresh Token**: 7일, httpOnly Secure 쿠키, Redis에 저장 + Rotation
- **RBAC**: USER / ADMIN 두 가지 역할

## 구현 현황

- [ ] User 엔티티 + JPA 설정
- [ ] 회원가입 API (`POST /api/v1/auth/register`)
- [ ] 로그인 API (`POST /api/v1/auth/login`)
- [ ] 토큰 갱신 API (`POST /api/v1/auth/refresh`)
- [ ] 로그아웃 API (`POST /api/v1/auth/logout`)
- [ ] RSA 키 생성 + JwtProvider 구현
- [ ] Redis Refresh Token 저장·검증·무효화
- [ ] Gateway JWT 검증 필터 완성 (Week 2 Day 5)
- [ ] Spring Security 설정 (SecurityFilterChain)
