# ecommerce-msa

MSA 기반 이커머스 플랫폼 (포트폴리오 프로젝트)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Spring Boot 3.x, Spring Security, Spring Data JPA, Spring Kafka |
| Gateway | Spring Cloud Gateway |
| Cache | Redis 7.x |
| DB | PostgreSQL 16.x |
| Message Broker | Apache Kafka (이벤트 기반 Saga) |
| Frontend | React 18, Axios |
| Infra | Docker, Docker Compose |

## 서비스 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| API Gateway | 8080 | 라우팅, JWT 검증 |
| Auth Service | 8081 | 회원/인증/RBAC |
| Product Service | 8082 | 상품 CRUD + Redis 캐싱 + 재고 차감(Redis Lock) |
| Order Service | 8083 | 주문 처리 + Kafka 이벤트 (Saga Choreography) |
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
