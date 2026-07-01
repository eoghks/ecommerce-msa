# ecommerce-msa

MSA 기반 이커머스 플랫폼 (포트폴리오 프로젝트)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Gateway | Spring Cloud Gateway (JWT RS256 검증 + JWKS) |
| Messaging | Apache Kafka (Saga Choreography) |
| Cache / Lock | Redis 7.x (캐싱, Redisson 분산 락, 멱등키, Refresh 토큰) |
| Storage | MinIO (S3 호환 이미지 스토리지) |
| DB | PostgreSQL 16 (서비스별 독립 스키마, Flyway 마이그레이션) |
| Frontend | React 19, Vite 8, Axios, Zustand, React Router v7, Tailwind CSS |
| Infra | Docker, Docker Compose |

## 서비스 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| API Gateway | 8080 | 라우팅, JWT 검증, X-User 헤더 주입/위조 차단 |
| Auth Service | 8081 | 회원/인증/RBAC, 판매자 승격, Refresh 토큰(HttpOnly 쿠키) |
| Product Service | 8082 | 상품 CRUD + Redis 캐싱 + 원자적 재고 차감/복구 + MinIO 업로드 |
| Order Service | 8083 | 주문 처리 + 항목 단위 취소 + Kafka 이벤트 (Saga Choreography) |
| Monitoring | 8084 | 서비스 헬스 대시보드 API (관리자 전용) |
| Frontend | 3000 | React SPA |

## 주요 기능

- **RBAC** — USER / SELLER / ADMIN 역할. 판매자 승격(mock 휴대폰 인증), 관리자 상품 판매 금지(BANNED)
- **주문 Saga** — Kafka Choreography로 주문 생성 → 재고 차감 → 보상. 원자적 재고 UPDATE(oversell 방지), 멱등 처리, DLT 보상 핸들러
- **주문 관리** — 판매자(자기 주문)·관리자(전체) 조회, 항목 단위 취소(사유 필수) 및 재고 복구
- **보안** — Gateway 헤더 위조 차단, 이미지 업로드 매직넘버 검증(저장형 XSS 차단), Refresh 토큰 원자적 회전(GETDEL), actuator 상세 비노출
- **모니터링** — 각 서비스 actuator 헬스를 집계하는 관리자 대시보드

## 문서

- [아키텍처](docs/architecture.md)
- [API 명세](docs/api-spec.md)
- [로드맵](docs/roadmap.md)
- [기술 선택 근거](docs/decisions/)

## 실행 방법

```bash
# 전체 서비스 실행
docker-compose up -d
```
