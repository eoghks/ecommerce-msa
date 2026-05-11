# Product Service — Redis 캐싱 & 재고 Lock 전략

---

## 1. 캐싱 정책 (Cache-Aside)

> 목록 캐시는 조건 조합이 무한대라 캐시 히트율이 낮고 관리 복잡도만 높아 제외.
> 메인 페이지 베스트/추천 상품 등 고정 목록 캐시는 추후 버전에서 추가 예정.

### 캐시 키 & TTL

| 키 패턴 | 저장 값 | TTL | 무효화 트리거 |
|---------|---------|-----|--------------|
| `product:detail:{productId}` | 상품 상세 JSON | 10분 | 해당 상품 수정/삭제 |

### 조회 흐름

```
클라이언트 GET /api/v1/products/{id}
  └─▶ ProductService.getProduct()
        ├─▶ Redis GET product:detail:{id}
        │     ├─ HIT  → JSON 반환
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

- `RedisTemplate<String, Object>` + `GenericJackson2JsonRedisSerializer`
- 역직렬화 시 타입 정보(`@class`) 포함하여 저장

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
