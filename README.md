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
| API Gateway | 8080 | 라우팅, JWT 검증, X-User 헤더 주입/위조 차단, 내부 전용 엔드포인트 차단 |
| Auth Service | 8081 | 회원/인증/RBAC, 판매자 승격, Refresh 토큰(HttpOnly 쿠키), 서비스 간 내부 토큰 |
| Product Service | 8082 | 상품 CRUD + 카테고리 관리 + 검색(정렬·가격필터·자동완성) + 리뷰·별점 + 위시리스트 + Redis 캐싱 + 원자적 재고 차감/복구 + MinIO 업로드 |
| Order Service | 8083 | 주문 처리(Saga) + 항목/주문 취소 + 배송지·배송상태 + 알림 + Kafka 이벤트 |
| Monitoring | 8084 | 서비스 헬스 대시보드 API (관리자 전용) |
| Frontend | 5173 | React SPA (Vite dev 서버) |

## 주요 기능

- **RBAC** — USER / SELLER / ADMIN 역할. 판매자 승격(mock 휴대폰 인증), 관리자 상품 판매 금지(BANNED)
- **상품** — 카테고리 관리(ADMIN), 검색 고도화(정렬 화이트리스트·가격대 필터·자동완성), 리뷰·별점(구매 인증 기반), 위시리스트(찜)
- **주문 Saga** — Kafka Choreography로 주문 생성 → 재고 차감 → 보상. 원자적 재고 UPDATE(oversell 방지), 멱등 처리, DLT 보상 핸들러
- **주문 관리** — 판매자(자기 주문)·관리자(전체) 조회, 항목/주문 단위 취소(사유·재고 복구), 실패 주문 관리자 조회
- **배송** — 배송지 주소록(기본 배송지), 주문 시 배송지 스냅샷, 배송 상태(준비중→배송중→배송완료)
- **알림** — 주문 확정/취소/배송상태 변경 시 인앱 알림, 미읽음 뱃지
- **보안** — Gateway 헤더 위조 차단, 서비스 간 내부 토큰(X-Internal-Token), 이미지 업로드 매직넘버 검증(저장형 XSS 차단), Refresh 토큰 원자적 회전(GETDEL), actuator 상세 비노출
- **모니터링** — 각 서비스 actuator 헬스를 집계하는 관리자 대시보드

## 문서

- [아키텍처](docs/architecture.md)
- [API 명세](docs/api-spec.md)
- [로드맵](docs/roadmap.md)
- [기능 설계 문서](docs/design/) — 위시리스트·검색·리뷰별점·배송지/배송상태·알림
- [배포 체크리스트](docs/deploy-checklist.md)
- [기술 선택 근거](docs/decisions/)

## 실행 방법

```bash
# 1. 인프라 기동 (PostgreSQL, Redis×3, Kafka, MinIO)
docker-compose up -d

# 2. 백엔드 서비스 기동 (각각)
./gradlew :gateway:bootRun
./gradlew :auth-service:bootRun
./gradlew :product-service:bootRun
./gradlew :order-service:bootRun
./gradlew :monitoring-service:bootRun

# 3. 프론트엔드 (http://localhost:5173)
cd frontend && npm install && npm run dev
```
