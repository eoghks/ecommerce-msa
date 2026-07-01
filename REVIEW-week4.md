# MSA 이커머스 Week 4 — 코드 리뷰 보고서

**리뷰 일시:** 2026-05-21  
**리뷰 범위:** Order Service / Product Service / Common  
**기반 스택:** Java 17, Spring Boot 3.x, Kafka, Redis(Redisson)  
**심각도 기준:** CRITICAL → HIGH → MEDIUM → LOW

---

## 요약

| 심각도   | 건수 |
|----------|------|
| CRITICAL | 3    |
| HIGH     | 3    |
| MEDIUM   | 4    |
| LOW      | 3    |
| **합계** | **13** |

Saga Choreography 패턴의 뼈대(이벤트 발행/구독, Redis 분산 락, 멱등성 키)는 잘 잡혀 있습니다.  
그러나 **트랜잭션 커밋 전 이벤트 발행**, **다중 상품 부분 차감 원자성 미보장**, **Kafka 발행 실패 무시** 세 가지는 운영 환경에서 데이터 정합성을 깨뜨릴 수 있는 CRITICAL 수준 결함입니다.

---

## CRITICAL

### C-01: 트랜잭션 커밋 전 Kafka 이벤트 발행 — 유령 주문 이벤트

**파일:** `order-service/.../OrderService.java:61-69`

**문제:**  
`createOrder()` 안에서 `orderRepository.save()` 후 **트랜잭션 커밋 이전에** `eventPublisher.publishOrderCreated()` 를 호출합니다.  
Kafka 전송은 즉시 브로커로 나가고, 이후 JPA flush/commit 단계에서 DB 예외가 발생하면 주문은 롤백되지만 이벤트는 이미 발송된 상태입니다.  
Product Service 는 존재하지 않는 orderId 의 재고를 차감하고 `stock.decreased` 를 발행 → Order Service 는 해당 주문을 찾지 못해 `OrderNotFoundException` 이 발생하는 Saga 파탄 시나리오가 성립합니다.

**수정 방법:**  
`@TransactionalEventListener(phase = AFTER_COMMIT)` 으로 DB 커밋 완료 시점에만 이벤트를 발행합니다.  
`OrderEventPublisher.publishOrderCreated()` 를 직접 호출하는 대신 `ApplicationEventPublisher` 로 Spring 이벤트를 등록하면, 리스너가 커밋 후에만 실행됩니다.

```java
// OrderService.java — 직접 호출 대신 Spring 이벤트로 등록
private final ApplicationEventPublisher applicationEventPublisher;

// ...
applicationEventPublisher.publishEvent(
        new OrderCreatedEvent(savedOrder.getId(), userId, payloads));

// OrderEventPublisher.java — AFTER_COMMIT 시점에만 Kafka 발행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publishOrderCreated(OrderCreatedEvent event) {
    kafkaTemplate.send(orderCreatedTopic, event.getOrderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("OrderCreatedEvent 발행 실패. orderId={}", event.getOrderId(), ex);
                } else {
                    log.info("OrderCreatedEvent 발행 완료. orderId={}", event.getOrderId());
                }
            });
}
```

---

### C-02: doDecreaseStock() — 다중 상품 부분 차감 원자성 미보장

**파일:** `product-service/.../StockDecreaseService.java:99-107`

**문제:**  
`doDecreaseStock()` 에 `@Transactional` 이 없습니다. 주석에 "self-invocation 회피를 위해 명시적 save" 라고 적혀 있으나, `productRepository.save()` 는 `SimpleJpaRepository` 자체 `@Transactional` 로 **상품별 독립 커밋**을 수행합니다.  
3개 상품 중 2번째에서 `IllegalStateException(재고 부족)` 이 발생하면, 1번째 상품의 차감은 **이미 DB에 커밋된 상태**로 남습니다.  
보상 이벤트(`stock.decrease.failed`)가 Order Service 로 전달되더라도, 선차감된 재고는 복구되지 않습니다.

**수정 방법:**  
`doDecreaseStock()` 을 별도 Spring Bean 으로 분리해 self-invocation 없이 `@Transactional` 을 적용합니다.

```java
// StockDecreaseTransactionService.java (신규 Bean)
@Service
@Transactional
@RequiredArgsConstructor
public class StockDecreaseTransactionService {
    private final ProductRepository productRepository;

    public void execute(OrderCreatedPayload payload) {
        for (OrderCreatedPayload.Item item : payload.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));
            product.decreaseStock(item.quantity()); // dirty check 자동 반영, 명시적 save 불필요
        }
        log.info("재고 차감 완료. orderId={}", payload.orderId());
    }
}

// StockDecreaseService 에서 주입 후 호출
stockDecreaseTransactionService.execute(payload);
```

---

### C-03: StockEventPublisher — Kafka 발행 실패 무시 → 주문 영구 PENDING

**파일:** `product-service/.../StockEventPublisher.java:25-36`

**문제:**  
`publishStockDecreased()` 와 `publishStockDecreaseFailed()` 모두 `kafkaTemplate.send()` 의 반환 `CompletableFuture` 를 무시합니다.  
재고 차감은 DB 에 커밋됐지만 Kafka 전송이 실패하면 Order Service 는 결과 이벤트를 수신하지 못해 주문이 **영원히 PENDING** 으로 잔류합니다.  
반대로 실패 이벤트 발행이 누락되면 보상 트랜잭션도 실행되지 않습니다.

**수정 방법:**  
발행 결과를 `whenComplete` 로 반드시 처리합니다. 최소한 오류 로그와 알람이 있어야 하며, 장기적으로는 Outbox 패턴 적용을 권장합니다.

```java
public void publishStockDecreased(Long orderId) {
    StockDecreasedEvent event = new StockDecreasedEvent(orderId);
    kafkaTemplate.send(stockDecreasedTopic, String.valueOf(orderId), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    // 운영: 알람 발송 또는 Outbox 테이블에 재시도 레코드 삽입 필요
                    log.error("재고 차감 성공 이벤트 발행 실패 — 수동 개입 필요. orderId={}", orderId, ex);
                } else {
                    log.info("재고 차감 성공 이벤트 발행 완료. orderId={}", orderId);
                }
            });
}

public void publishStockDecreaseFailed(Long orderId, String reason) {
    StockDecreaseFailedEvent event = new StockDecreaseFailedEvent(orderId, reason);
    kafkaTemplate.send(stockDecreaseFailedTopic, String.valueOf(orderId), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("재고 차감 실패 이벤트 발행 실패 — 수동 개입 필요. orderId={}", orderId, ex);
                } else {
                    log.warn("재고 차감 실패 이벤트 발행 완료. orderId={}, reason={}", orderId, reason);
                }
            });
}
```

---

## HIGH

### H-01: X-User-Id 헤더 — 게이트웨이 우회 시 권한 탈취 가능

**파일:** `order-service/.../OrderController.java:32, 42, 52, 60`

**문제:**  
`@RequestHeader("X-User-Id") Long userId` 는 HTTP 클라이언트가 임의 값을 넣어 타인 주문에 접근하거나, 타인 명의로 주문을 생성할 수 있습니다.  
현재 코드는 API Gateway 가 JWT 검증 후 신뢰된 헤더를 주입한다는 전제에 의존하지만, 그 전제를 서비스 레벨에서 강제하는 장치가 없습니다.  
내부 네트워크에서 게이트웨이를 거치지 않고 직접 호출하는 경우 완전히 무방비입니다.

**수정 방법 (단기):**  
Gateway 에서 설정한 내부 서명 헤더(예: `X-Internal-Token`) 를 Order Service 에서 검증합니다.

**수정 방법 (중기):**  
Spring Security + JWT 필터를 각 서비스에 추가해 토큰 재검증을 수행합니다.

---

### H-02: 멱등성 키 선발행 후 doDecreaseStock 실패 — 영구 skip 위험

**파일:** `product-service/.../StockDecreaseService.java:62-74`

**문제:**  
`setIfAbsent(processedKey, ...)` 로 멱등성 키를 **재고 차감 성공 이전에** 먼저 설정합니다.  
`doDecreaseStock()` 에서 예외가 발생하면 `delete(processedKey)` 로 키를 제거하지만, Redis 네트워크 단절 또는 JVM 크래시 발생 시 키 제거가 누락되어 이후 Kafka 재전달 메시지가 **영구적으로 skip** 됩니다.  
재고는 차감되지 않았는데 처리된 것으로 기록되는 상태가 됩니다.

**수정 방법:**  
멱등성 키를 재고 차감 **성공 이후** 에 설정하거나, 키 값을 상태(`PROCESSING` / `SUCCESS`)로 구분해 `PROCESSING` 상태는 재처리를 허용합니다.

```java
// 차감 성공 후 키 설정
doDecreaseStock(payload);

// 성공 후 멱등성 키 기록
redisTemplate.opsForValue().set(processedKey, "1", PROCESSED_TTL);
```

---

### H-03: KafkaConfig 완전 중복 — 양 서비스 동일 코드

**파일:**  
- `order-service/.../config/KafkaConfig.java`  
- `product-service/.../config/KafkaConfig.java`

**문제:**  
두 파일의 `errorHandler()` Bean 구현이 줄 단위로 동일합니다. DLT 파티션 지정 로직, `FixedBackOff(1000L, 2L)` 설정, 로그 포맷이 모두 같습니다.  
한 서비스만 수정하면 재시도 횟수·간격이 서비스 간에 달라지는 유지보수 리스크가 생깁니다.  
추가로 `product-service/KafkaConfig.java` 5번 줄에 사용되지 않는 `import org.apache.kafka.clients.consumer.ConsumerRecord;` 가 있습니다.

**수정 방법:**  
`common` 모듈에 `KafkaErrorHandlerConfig` 를 작성하고 `@ConditionalOnMissingBean` 으로 등록합니다.

```java
// common/.../config/KafkaErrorHandlerConfig.java
@Configuration
@ConditionalOnMissingBean(DefaultErrorHandler.class)
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Kafka 메시지 처리 최종 실패 → DLT 전송. topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
```

---

## MEDIUM

### M-01: confirm() / cancel() — 상태 전이 검증 없음

**파일:** `order-service/.../Order.java:68-75`

**문제:**  
`confirm()` 과 `cancel()` 이 현재 상태를 확인하지 않고 무조건 덮어씁니다.  
Kafka at-least-once 재전달 시나리오에서 이미 `CANCELLED` 된 주문에 `confirm()` 이 호출되면 `CONFIRMED` 로 변경됩니다.  
`CONFIRMED` 주문에 사용자가 취소를 요청할 경우 `isCancellable()` 은 막지만, 이벤트 경로로는 상태가 덮어써질 수 있습니다.

**수정 방법:**

```java
public void confirm() {
    if (this.status != OrderStatus.PENDING) {
        log.warn("confirm 호출 무시 — 이미 처리된 주문. orderId={}, status={}", id, status);
        return; // 멱등 처리
    }
    this.status = OrderStatus.CONFIRMED;
}

public void cancel() {
    if (this.status == OrderStatus.CANCELLED) {
        return; // 멱등 처리
    }
    if (this.status == OrderStatus.CONFIRMED) {
        throw new OrderNotCancellableException(id, status);
    }
    this.status = OrderStatus.CANCELLED;
}
```

---

### M-02: cancelByUser() — IllegalStateException 이 HTTP 500 으로 응답될 수 있음

**파일:** `order-service/.../OrderService.java:131-133`

**문제:**  
`IllegalStateException` 을 던지지만 전역 `@ExceptionHandler` 매핑이 없으면 Spring Boot 기본 동작으로 **500 Internal Server Error** 가 반환됩니다.  
CONFIRMED/CANCELLED 주문 취소 시도는 클라이언트 오류(409 Conflict)이므로 5xx 는 부적절합니다.

**수정 방법:**

```java
// 도메인 예외 클래스
@ResponseStatus(HttpStatus.CONFLICT)
public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(Long orderId, OrderStatus status) {
        super("취소할 수 없는 주문 상태입니다. orderId=" + orderId + ", status=" + status);
    }
}

// OrderService.cancelByUser() 에서 교체
throw new OrderNotCancellableException(orderId, order.getStatus());
```

---

### M-03: StockDecreasedEvent / StockDecreaseFailedEvent — 역직렬화 기본 생성자에서 orderId null 허용

**파일:**  
- `common/.../StockDecreasedEvent.java:20-23`  
- `common/.../StockDecreaseFailedEvent.java:20-26`

**문제:**  
Jackson 역직렬화용 기본 생성자에서 `orderId = null` 로 초기화합니다.  
Kafka 메시지가 손상되거나 스키마 불일치로 `orderId` 가 누락된 채 역직렬화되면, `StockEventConsumer` 에서 `event.getOrderId()` 를 검증 없이 전달해 NPE 또는 잘못된 주문 처리가 발생합니다.

**수정 방법:**  
`@JsonCreator` + `@JsonProperty` 로 기본 생성자를 제거하거나, Consumer 에서 null 체크를 추가합니다.

```java
// 기본 생성자 제거 + Jackson 생성자 어노테이션 적용
@JsonCreator
public StockDecreasedEvent(
        @JsonProperty("orderId") Long orderId) {
    super("STOCK_DECREASED");
    this.orderId = Objects.requireNonNull(orderId, "orderId는 필수값입니다");
}
```

---

### M-04: OrderService.createOrder() — @Transactional 내 외부 HTTP 호출로 DB 커넥션 점유

**파일:** `order-service/.../OrderService.java:37-75`

**문제:**  
`@Transactional` 범위 안에서 `productClient.getProduct()` (외부 HTTP 호출) 를 루프로 실행합니다.  
Product Service 응답이 느리거나 타임아웃이 발생하는 동안 JPA DB 커넥션이 계속 점유됩니다.  
주문 아이템 수가 많을수록 커넥션 점유 시간이 길어져 커넥션 풀 고갈로 이어질 수 있습니다.

**수정 방법:**  
상품 조회 스트림을 트랜잭션 시작 전에 완료하거나, `ProductClient` 에 Resilience4j CircuitBreaker + Timeout 을 적용합니다.

```java
// @Transactional 밖(별도 메서드)에서 상품 정보 선조회
List<OrderItem> items = buildOrderItems(request); // @Transactional 없는 메서드

@Transactional
public OrderResponse saveOrderAndPublish(Long userId, List<OrderItem> items) {
    // DB 저장 + 이벤트 발행만 수행
}
```

---

## LOW

### L-01: product-service KafkaConfig — 미사용 import

**파일:** `product-service/.../config/KafkaConfig.java:5`

```java
import org.apache.kafka.clients.consumer.ConsumerRecord; // 미사용 — 제거 필요
```

---

### L-02: StockEventPublisher — 성공/실패 발행 메서드 구조 중복

**파일:** `product-service/.../StockEventPublisher.java:25-36`

**문제:**  
`publishStockDecreased()` 와 `publishStockDecreaseFailed()` 의 `kafkaTemplate.send()` 호출 구조가 동일합니다.  
C-03 수정 시 공통 `sendWithLogging(String topic, String key, Object event)` private 메서드로 추출하면 중복을 제거할 수 있습니다.

---

### L-03: OrderCreatedEvent — items 리스트 방어적 복사 미적용

**파일:** `order-service/.../OrderCreatedEvent.java:12-19`

**문제:**  
`items` 필드에 외부에서 전달된 리스트 참조를 그대로 저장합니다.  
이벤트 객체 생성 이후 원본 리스트가 변경되면 이벤트 내부 상태도 변경됩니다.

**수정 방법:**

```java
this.items = List.copyOf(items); // 불변 복사
```

---

_리뷰일: 2026-05-21_  
_리뷰어: Claude (gsd-code-reviewer) — standard depth_
