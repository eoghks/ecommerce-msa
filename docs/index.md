# 기능 → 파일 크로스 레퍼런스

기능 구현 시 이 파일에 행을 추가합니다.  
추가 규칙: [docs-rule.md](../rules/docs-rule.md)

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
> 구현 예정: Week 2

| 기능 | 구현 파일 | 서비스 문서 | 관련 규칙 |
|------|---------|-----------|---------|
| (Week 2 구현 후 추가) | | | |

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
