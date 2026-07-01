# 재고 동시성 제어 — 비관적 락 vs 분산락

> 면접 대비 정리 문서

---

## 왜 동시성 문제가 생기나

```
재고: 1개

스레드A: SELECT stock → 1개 확인
스레드B: SELECT stock → 1개 확인   ← A가 커밋하기 전에 B도 조회
스레드A: stock = 0 → UPDATE → COMMIT
스레드B: stock = 0 → UPDATE → COMMIT  ← 둘 다 성공, 실제 재고는 -1
```

DB 조회와 UPDATE 사이의 간격에 다른 트랜잭션이 끼어드는 **TOCTOU(Time Of Check To Time Of Use)** 문제.

---

## 1. 비관적 락 (Pessimistic Lock)

### 동작 방식

```
스레드A: SELECT * FROM product WHERE id=1 FOR UPDATE  ← DB 행 잠금
스레드B: SELECT * FROM product WHERE id=1 FOR UPDATE  ← 블로킹 (대기)

스레드A: stock 확인 → 차감 → UPDATE → COMMIT → 락 해제
스레드B: 락 획득 → stock 재확인 → 차감 or 재고부족 예외
```

### JPA 구현

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

### 특징

| 항목 | 내용 |
|------|------|
| 락 위치 | DB (행 레벨) |
| 락 시점 | SELECT 시점 |
| 대기 방식 | DB 커넥션 붙잡고 블로킹 |
| 멀티 인스턴스 | 같은 DB를 바라보면 동작 |
| 구현 복잡도 | 낮음 |

### 장단점

**장점**
- 구현 단순 — 어노테이션 하나
- 데이터 정합성 100% 보장
- 추가 인프라 불필요

**단점**
- DB 커넥션을 락 해제까지 점유 → 트래픽 많으면 커넥션 풀 고갈
- 대기 중인 트랜잭션이 많아지면 전체 처리량 저하
- 데드락 위험 (여러 행을 순서 다르게 잠글 때)

---

## 2. 분산락 (Distributed Lock — Redisson)

### 동작 방식

```
스레드A: Redis SET stock:lock:1 NX PX 5000  ← 락 획득 성공
스레드B: Redis SET stock:lock:1 NX PX 5000  ← 실패 → 재시도 대기

스레드A: DB 조회 → 재고 차감 → COMMIT → Redis DEL stock:lock:1
스레드B: 락 획득 → DB 조회 → 차감 or 재고부족 예외
```

### Redisson 구현

```java
RLock lock = redissonClient.getLock("stock:lock:" + productId);

boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
// waitTime=3s: 락 대기 최대 시간
// leaseTime=5s: 락 자동 해제 시간 (데드락 방지)

if (!acquired) {
    throw new StockLockTimeoutException();
}
try {
    // DB 재고 차감 로직
} finally {
    lock.unlock();
}
```

### 특징

| 항목 | 내용 |
|------|------|
| 락 위치 | Redis |
| 락 시점 | 비즈니스 로직 진입 전 |
| 대기 방식 | Redis pub/sub 기반 (스핀락 아님) |
| 멀티 인스턴스 | ✅ 완전 지원 |
| 구현 복잡도 | 중간 |

### 장단점

**장점**
- DB 커넥션을 짧게 사용 → 고트래픽에서 유리
- 멀티 인스턴스(MSA) 환경에서 완전 동작
- leaseTime으로 데드락 자동 방지

**단점**
- Redis 장애 시 락 불가 → Redis 고가용성 필요 (Sentinel/Cluster)
- 추가 인프라(Redis) 의존
- 구현 복잡도 증가

---

## 3. 비교 정리

| 항목 | 비관적 락 | 분산락 |
|------|----------|--------|
| 락 위치 | DB | Redis |
| DB 커넥션 점유 | 락 해제까지 | 짧게 (쿼리 시간만) |
| 멀티 인스턴스 | 같은 DB면 가능 | 완전 지원 |
| 추가 인프라 | 없음 | Redis 필요 |
| 구현 복잡도 | 낮음 | 중간 |
| 고트래픽 적합성 | 낮음 | 높음 |
| 장애 포인트 | DB | DB + Redis |

---

## 4. 실무 선택 기준

| 상황 | 선택 |
|------|------|
| 단일 인스턴스, 낮은 트래픽 | 비관적 락 |
| MSA, 멀티 인스턴스 | 분산락 |
| 핫딜/선착순 이벤트 | Redis DECR 선차감 + Kafka 후처리 |
| 카카오/쿠팡급 | Redis 재고 선차감 → DB 비동기 동기화 |

---

## 5. 이 프로젝트 전략

- **Week 3**: 비관적 락 — 단일 인스턴스, 구현 단순
- **Week 4**: Redisson 분산락으로 전환 — Order Service 연동 + MSA 환경 대응

"트래픽 증가를 고려해 비관적 락으로 먼저 구현 후 분산락으로 전환" — 면접 스토리라인

---

## 6. 면접 예상 질문

**Q. 재고 동시성을 어떻게 처리했나요?**
> 비관적 락으로 시작해서 MSA 환경에서 Redisson 분산락으로 전환했습니다. 비관적 락은 DB 커넥션을 락 해제까지 점유해서 고트래픽에서 커넥션 풀 고갈 위험이 있고, 분산락은 Redis에서 락을 관리해 DB 부하를 줄이고 멀티 인스턴스에서도 정합성을 보장합니다.

**Q. 낙관적 락은 왜 안 썼나요?**
> 낙관적 락은 충돌 빈도가 낮을 때 적합합니다. 재고 차감은 동시 요청이 집중되는 케이스라 충돌 시 재시도 로직이 복잡해지고 사용자 경험도 나빠서 비관적 락을 선택했습니다.

**Q. Redis 장애 시 어떻게 되나요?**
> Redis 장애 시 분산락 획득 자체가 불가능해져 주문이 안 됩니다. 운영 환경에서는 Redis Sentinel 또는 Cluster로 고가용성을 확보하고, 장애 시 비관적 락으로 폴백하는 전략을 고려할 수 있습니다.
