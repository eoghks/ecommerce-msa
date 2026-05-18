# Product Service — Redis 캐싱 & 재고 Lock 전략

---

## 1. 캐싱 정책 (Cache-Aside)

> 목록 캐시는 조건 조합이 무한대라 캐시 히트율이 낮고 관리 복잡도만 높아 제외.
> 메인 페이지 베스트/추천 상품 등 고정 목록 캐시는 추후 버전에서 추가 예정.

### Redis 인스턴스 분리

서비스별 Redis 인스턴스를 분리하여 장애 격리 및 eviction 정책을 독립적으로 적용.

| 서비스 | 포트 | maxmemory | maxmemory-policy | 이유 |
|--------|------|-----------|-----------------|------|
| auth-service | 6379 | 128mb | `volatile-lru` | Refresh Token 보호 (TTL 있는 키만 삭제) |
| product-service | 6380 | 256mb | `allkeys-lru` | 캐시 용도, 전체 키 LRU 삭제 적합 |
| order-service | 6381 | 128mb | `volatile-lru` | 분산락 키 보호 |

> `allkeys-lru` vs `volatile-lru`: auth/order는 세션·락 데이터가 날아가면 안 되므로 TTL 없는 키는 삭제하지 않는 `volatile-lru` 적용.

### 캐시 키 & TTL

| 키 패턴 | 저장 값 | TTL | 무효화 트리거 |
|---------|---------|-----|--------------|
| `product:detail:{productId}` | 상품 상세 JSON | 10분 (조회 시 리셋) | 해당 상품 수정/삭제 |

> 조회할 때마다 TTL을 10분 리셋하여 자주 조회되는 인기 상품은 캐시에서 유지.  
> `allkeys-lru` 정책과 조합되어 인기 상품은 메모리 부족 시에도 삭제 후순위가 됨.

### 조회 흐름

```
클라이언트 GET /api/v1/products/{id}
  └─▶ ProductService.getProduct()
        ├─▶ Redis GET product:detail:{id}
        │     ├─ HIT  → TTL 10분 리셋 → JSON 반환
        │     └─ MISS → DB 조회 → Redis SET (TTL 10분) → 반환
```

### 쓰기 시 캐시 무효화

```
상품 수정/삭제 발생
  └─▶ ProductService.update/delete()
        ├─▶ DB 트랜잭션 처리
        └─▶ Redis DEL product:detail:{id}  (단건 삭제)
```

### 직렬화

- `RedisTemplate<String, String>` + 전용 `ObjectMapper` 빈
- 값은 `ObjectMapper.writeValueAsString(ProductResponse)` → 순수 JSON 문자열로 저장
- 역직렬화는 `ObjectMapper.readValue(json, ProductResponse.class)` 명시적 타입 지정
- `JavaTimeModule` 등록으로 `LocalDateTime` 직렬화 지원
- `GenericJackson2JsonRedisSerializer` 미사용 이유: `@class` 타입 정보가 JSON에 삽입되어 패키지 이름 변경 시 역직렬화 실패, 보안 위험(`DefaultTyping`) 존재

---

## 2. 재고 분산락 (Redisson — Week 4)

> Week 4 Order Service 연동 시점에 활성화. 아래는 설계 기준 문서.

### 락 키 패턴

```
stock:lock:{productId}
```

### 재고 차감 흐름

```
OrderCreatedEvent 수신 (Kafka)
  └─▶ StockService.decreaseStock(productId, quantity)
        ├─▶ RLock lock = redissonClient.getLock("stock:lock:{productId}")
        ├─▶ lock.tryLock(waitTime=3s, leaseTime=5s, SECONDS)
        │     ├─ 획득 실패 → StockLockTimeoutException (재시도는 Kafka 재소비)
        │     └─ 획득 성공
        │           ├─▶ DB 재고 조회 (비관적 락 또는 낙관적 락 병행 가능)
        │           ├─▶ stock < quantity → StockInsufficientException
        │           ├─▶ product.decreaseStock(quantity) → save
        │           └─▶ lock.unlock()
        └─▶ 캐시 무효화: product:detail:{productId}
```

### 락 파라미터

| 파라미터 | 값 | 설명 |
|---------|-----|------|
| waitTime | 3초 | 락 대기 최대 시간 |
| leaseTime | 5초 | 락 자동 해제 시간 (데드락 방지) |
| 구현체 | Redisson `RLock` | `tryLock` 사용 — 무한 대기 금지 |

### 예외 처리

| 예외 | 의미 | 처리 |
|------|------|------|
| `StockInsufficientException` | 재고 부족 | Kafka 보상 이벤트 발행 (`StockRestoredEvent` — 실제론 취소 이벤트) |
| `StockLockTimeoutException` | 락 획득 실패 | Kafka 재소비 (DLT 3회 초과 시 DLT 토픽 이동) |

---

## 3. 토이 vs 운영 판단

| 항목 | 토이 구현 | 운영 수준 |
|------|----------|----------|
| 캐시 무효화 | SCAN+DEL (단일 Redis) | Redis Cluster — Lua 스크립트 원자 삭제 |
| 재고 Lock | Redisson RLock | Redisson RLock + DB 낙관적 락 이중화 |
| 캐시 직렬화 | Jackson JSON | Protobuf / MessagePack (속도 최적화) |
| 재고 이벤트 | Kafka 단순 소비 | Outbox Pattern (트랜잭션 보장) |
