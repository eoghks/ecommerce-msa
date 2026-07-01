# Docker Compose — 인프라 구성

## 포함된 서비스

| 서비스 | 포트 | 컨테이너명 |
|--------|------|----------|
| PostgreSQL 16 | 5432 | ecommerce-postgres |
| Redis 7.2 | 6379 | ecommerce-redis |
| Zookeeper | 2181 | ecommerce-zookeeper |
| Kafka 3.5 (Confluent 7.5) | 9092 (외부) / 29092 (내부) | ecommerce-kafka |
| Kafka UI | 8090 | ecommerce-kafka-ui |

## 실행

```bash
# docker/ 디렉토리에서
cp .env.example .env       # 최초 1회
docker compose up -d
docker compose ps          # 상태 확인
docker compose logs -f kafka  # 로그 확인
docker compose down        # 종료 (볼륨 유지)
docker compose down -v     # 종료 + 볼륨 삭제 (초기화)
```

## DB 접속 확인

```bash
# 컨테이너 내부 접속
docker exec -it ecommerce-postgres psql -U eoghks -d postgres

# DB 목록 확인
\l
# auth_db, product_db, order_db 가 보여야 정상
```

## Redis 접속 확인

```bash
docker exec -it ecommerce-redis redis-cli ping
# PONG 반환되면 정상
```

## Kafka 동작 확인

브라우저: http://localhost:8090 (Kafka UI)

또는 CLI:
```bash
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## 네트워크 구조

- 모든 서비스는 `ecommerce-net` 브리지 네트워크에 연결
- 컨테이너 간 통신은 컨테이너명 사용 (예: `kafka:29092`, `postgres:5432`)
- 호스트(로컬 PC)에서 접속 시 `localhost:<외부 포트>`

## 보안 메모

- 로컬 전용 설정 — 비밀번호는 단순값 (`eoghks_local`)
- 운영 환경엔 절대 사용 금지
- `.env` 파일은 `.gitignore` 포함, 절대 커밋 금지
