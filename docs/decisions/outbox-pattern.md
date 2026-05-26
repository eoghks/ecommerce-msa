# Outbox Pattern — 미구현 설계 문서

## 왜 Outbox Pattern이 필요한가

### 현재 구현의 한계 (Kafka 직접 발행 방식)

```
@Transactional
public OrderResponse createOrder(...) {
    Order savedOrder = orderRepository.save(order);   // DB 저장
    eventPublisher.publishOrderCreated(event);        // Kafka 발행
}
```

**문제**: DB 커밋은 성공했는데 Kafka 발행이 실패하면 이벤트 유실.
반대로 Kafka 발행 후 DB 롤백되면 이벤트는 나갔는데 데이터는 없는 상태.

→ DB 트랜잭션과 Kafka 발행은 **원자적으로 묶을 수 없음**.

---

## Outbox Pattern 개념

이벤트를 Kafka에 직접 발행하지 않고 **같은 DB 트랜잭션 안에 outbox 테이블에 저장**.
별도 프로세스(Relay)가 outbox 테이블을 폴링하여 Kafka로 발행.

```
[애플리케이션 트랜잭션]
  orders 테이블 INSERT
  order_outbox 테이블 INSERT  ← 이벤트 저장
  → DB 커밋 (원자적)

[Relay 프로세스 — 별도]
  order_outbox 폴링 (미발행 레코드 조회)
  → Kafka 발행 성공 시 published_at 업데이트
  → 실패 시 재시도
```

---

## 테이블 설계

```sql
CREATE TABLE order_outbox (
    id          BIGSERIAL PRIMARY KEY,
    aggregate_id    BIGINT       NOT NULL,          -- orderId
    event_type      VARCHAR(100) NOT NULL,          -- ORDER_CREATED
    payload         TEXT         NOT NULL,          -- JSON 직렬화 이벤트
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,                      -- NULL = 미발행
    retry_count     INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON order_outbox (published_at) WHERE published_at IS NULL;
```

---

## Relay 구현 방식 (두 가지)

### 1. Polling Publisher (단순, 이 프로젝트 적용 시 선택)

```java
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<OrderOutbox> unpublished = outboxRepository.findUnpublished();
    for (OrderOutbox outbox : unpublished) {
        try {
            kafkaTemplate.send(topic, outbox.getPayload());
            outbox.markPublished();
            outboxRepository.save(outbox);
        } catch (Exception e) {
            outbox.incrementRetry();
            outboxRepository.save(outbox);
        }
    }
}
```

- 장점: 구현 단순
- 단점: 폴링 주기만큼 지연 발생, DB 부하

### 2. CDC (Change Data Capture) — Debezium

- DB binlog(WAL) 변경을 캡처해서 Kafka로 발행
- 폴링 없이 실시간, DB 부하 없음
- 단점: Debezium 인프라 필요, 운영 복잡도 상승
- 대규모 실무에서 선택하는 방식

---

## 멱등성 처리

Relay가 재시도할 때 중복 발행 가능 → Consumer 쪽에서 멱등성 보장 필요.

```java
// 이미 처리된 orderId면 skip
if (processedEventRepository.existsByOrderId(event.getOrderId())) {
    return;
}
```

---

## 현재 프로젝트 미구현 이유

| 항목 | 내용 |
|------|------|
| 구현 범위 | 7주 일정 내 핵심 기능 우선 |
| 대안 | Kafka 재시도 + DLT로 운영 수준 안정성 확보 |
| 실무 적용 기준 | 주문량이 많아 이벤트 유실이 비즈니스 손실로 직결될 때 도입 |

---

## 면접 답변 포인트

> "현재는 Kafka 직접 발행 방식을 사용하고 있습니다. 트랜잭션과 이벤트 발행의 원자성 문제는 알고 있으며,
> 이를 해결하려면 Outbox Pattern을 적용해야 합니다.
> Polling Publisher 방식은 구현이 단순하고, 대규모 트래픽에서는 Debezium CDC를 선택합니다.
> 이번 프로젝트에서는 일정상 Kafka 재시도 + DLT로 대체했습니다."
