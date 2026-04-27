# 분산 트랜잭션 — Saga 패턴 (Choreography)

## 배경
이커머스 주문 생성 시 여러 서비스에 걸쳐 데이터 변경 발생.
- **Order Service**: 주문 생성
- **Product Service**: 재고 차감
- **(향후) Payment Service**: 결제 승인

각 서비스가 별도 DB(서비스별 PostgreSQL)를 사용하므로 단일 `@Transactional` 로 묶을 수 없음.

---

## 후보 비교

### 1. 2PC (Two-Phase Commit)
- ❌ 성능·가용성 저하, MSA 환경 부적합

### 2. Saga — Orchestration (중앙 조정자)
- 한 곳에 흐름 명시, 추적 쉬움
- 단일 장애점, 서비스 간 결합도 높음

### 3. Saga — Choreography (이벤트 기반) ✅ **선택**
- 각 서비스가 이벤트 발행/구독으로 자율 동작
- 결합도 낮음, 확장성 우수
- 흐름 추적 어려움 → 분산 추적·로그 정비로 보완

---

## 선택: Choreography + Kafka

**근거:**
1. 서비스 간 결합도 최소화 — 각 서비스 독립 개발/배포 가능
2. 확장성 — 새 서비스 추가 시 이벤트 구독만 추가하면 됨
3. **포트폴리오 어필** — Kafka 활용 경험 강조 가능
4. 비동기 처리로 응답 속도 개선

---

## 메시지 브로커: Apache Kafka

**Kafka 선택 이유**
- 이벤트 영속화 → 장애 복구·재처리 용이
- 컨슈머 그룹 → 서비스 확장 시 자연스럽게 분산
- 면접 어필 가치가 RabbitMQ 대비 높음

---

## 적용 시나리오 — 주문 생성

### 정상 플로우 (이벤트 흐름)
```
1. Order Service
   - 주문 PENDING 생성
   - Kafka 이벤트 발행: OrderCreated

2. Product Service (OrderCreated 구독)
   - 재고 차감
   - 이벤트 발행: StockReserved (성공) 또는 StockReserveFailed (실패)

3. (향후) Payment Service (StockReserved 구독)
   - 결제 승인
   - 이벤트 발행: PaymentCompleted

4. Order Service (PaymentCompleted 구독)
   - 주문 상태 PAID 로 변경
```

### 실패 시 — 보상 이벤트 흐름
```
[재고 부족 시]
- Product Service → StockReserveFailed 이벤트 발행
- Order Service (구독) → 주문 상태 CANCELLED 로 변경

[결제 실패 시]
- Payment Service → PaymentFailed 이벤트 발행
- Product Service (구독) → 재고 복구 (StockRestored 이벤트 발행)
- Order Service (구독) → 주문 상태 CANCELLED 로 변경
```

---

## 이벤트 정의

| 이벤트 | 발행자 | 구독자 |
|--------|--------|--------|
| `OrderCreated` | Order Service | Product Service |
| `StockReserved` | Product Service | (Payment Service) |
| `StockReserveFailed` | Product Service | Order Service |
| `PaymentCompleted` | Payment Service | Order Service |
| `PaymentFailed` | Payment Service | Product Service |
| `StockRestored` | Product Service | Order Service |

이벤트 페이로드는 `eventId`, `occurredAt`, `correlationId`(주문ID) 공통 필드 + 도메인 데이터.

---

## 구현 가이드

### Kafka 설정
- 토픽 분리: 도메인별 (`order-events`, `product-events`, `payment-events`)
- 파티션 키: `correlationId`(주문ID) → 같은 주문 이벤트는 동일 파티션 → 순서 보장

### 이벤트 발행
- `@TransactionalEventListener(AFTER_COMMIT)` 으로 DB 커밋 이후 발행
- **Outbox 패턴 권장** — DB 트랜잭션과 이벤트 발행 원자성 보장
  - 이벤트를 `outbox` 테이블에 INSERT (같은 트랜잭션)
  - 별도 Polling/CDC 가 outbox → Kafka 로 전송

### 이벤트 소비
- 멱등(idempotent) 처리 필수 — `eventId` 중복 처리 방지 (`processed_events` 테이블 활용)
- 실패 시 재시도, 일정 횟수 초과 시 DLQ(Dead Letter Queue) 로 이동
- Spring Kafka `@KafkaListener` 사용

### 분산 추적
- 모든 이벤트에 `correlationId` 포함 → 주문 1건의 흐름 추적
- MDC 에 `correlationId` 주입 → 로그에 자동 포함
- (선택) Zipkin/Jaeger 로 시각화

---

## 한계 및 보완책
| 한계 | 보완 |
|------|------|
| 흐름 추적 어려움 | `correlationId` + 분산 추적 도구 |
| 일관성 지연 (eventually consistent) | UI 에서 "처리 중" 상태 표시 |
| 이벤트 유실 위험 | Outbox 패턴 + Kafka 영속화 |
| 중복 소비 | `eventId` 기반 멱등 처리 |
