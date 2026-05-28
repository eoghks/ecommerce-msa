# ecommerce-msa

MSA 기반 이커머스 플랫폼 (포트폴리오 프로젝트)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Gateway | Spring Cloud Gateway |
| Cache | Redis 7.x |
| DB | PostgreSQL 16 |
| Frontend | React 19, Vite 8, Axios, Zustand, React Router v7, Tailwind CSS |
| Infra | Docker, Docker Compose |

## 서비스 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| API Gateway | 8080 | 라우팅, JWT 검증 |
| Auth Service | 8081 | 회원/인증/RBAC |
| Product Service | 8082 | 상품 CRUD + Redis 캐싱 |
| Order Service | 8083 | 주문/재고 + Redis Lock |
| Monitoring | 8084 | 헬스체크 대시보드 |
| Frontend | 3000 | React SPA |

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
