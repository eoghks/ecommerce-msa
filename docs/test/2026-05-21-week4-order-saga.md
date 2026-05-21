# Week 4 테스트 결과 — Order Service + Saga (2026-05-21)

## 실행 결과 요약

| 서비스 | 테스트 수 | 통과 | 실패 | Skip |
|--------|----------|------|------|------|
| order-service | 23 | 23 | 0 | 0 |
| product-service | 39 | 29 | 0 | 10 |

> product-service skip 10건: `ProductControllerE2ETest` — docker-compose 기동 필요 (`-De2e=true` 플래그로 활성화)

---

## order-service 테스트 상세

### OrderServiceTest (단위) — 12건
| 테스트 | 설명 |
|--------|------|
| createOrder_success | 주문 생성 + ApplicationEvent 발행 |
| confirmOrder_success | PENDING → CONFIRMED |
| confirmOrder_idempotent | 이미 CONFIRMED 상태 멱등 처리 |
| confirmOrder_notFound | 존재하지 않는 주문 404 |
| cancelOrder_saga_success | PENDING → CANCELLED (Saga 보상) |
| cancelOrder_idempotent | 이미 CANCELLED 상태 멱등 처리 |
| getMyOrders_success | userId 기준 페이징 조회 |
| getOrder_success | 본인 주문 상세 조회 |
| getOrder_otherUser_notFound | 타인 주문 조회 404 |
| cancelByUser_success | PENDING 주문 취소 |
| cancelByUser_alreadyConfirmed | CONFIRMED 주문 취소 시도 409 |
| cancelByUser_otherUser | 타인 주문 취소 시도 404 |

### OrderDomainTest (단위) — 8건
주문 도메인 상태 전이 및 검증 테스트

### OrderSagaIntegrationTest (통합) — 2건
| 테스트 | 설명 |
|--------|------|
| sagaFlow_stockDecreased_confirmsOrder | stock.decreased 수신 → CONFIRMED |
| sagaFlow_stockDecreaseFailed_cancelsOrder | stock.decrease.failed 수신 → CANCELLED |

---

## product-service 테스트 상세

### StockDecreaseServiceTest (단위) — 4건
| 테스트 | 설명 |
|--------|------|
| decreaseStock_success | 재고 차감 성공 → stock.decreased 발행 |
| decreaseStock_alreadyProcessed_skip | 멱등성 키 존재 시 skip |
| decreaseStock_insufficientStock_publishFailed | 재고 부족 → stock.decrease.failed 발행 |
| decreaseStock_lockFailed_publishFailed | Redis 락 획득 실패 → stock.decrease.failed 발행 |

### StockDecreaseSagaIntegrationTest (통합) — 2건
| 테스트 | 설명 |
|--------|------|
| sagaFlow_decreaseStock_success | order.created 수신 → 재고 차감 → stock.decreased 발행 |
| sagaFlow_insufficientStock_publishFailed | 재고 부족 → stock.decrease.failed 발행 |

---

## 주요 수정 사항 (코드 품질 리팩토링)

| 항목 | 내용 |
|------|------|
| C-01 | `@TransactionalEventListener(AFTER_COMMIT)` — DB 커밋 후 Kafka 발행 보장 |
| C-02 | `StockDecreaseTransactionService` 분리 — self-invocation AOP 우회 해결 |
| C-03 | `StockEventPublisher.whenComplete()` 핸들러 추가 |
| H-02 | 멱등성 키를 재고 차감 성공 후 설정 (JVM 크래시 방지) |
| M-04 | `productClient.getProduct()` 를 `@Transactional` 경계 밖으로 이동 |
| E2E  | `@EnabledIfSystemProperty(named="e2e")` 조건부 실행 가드 추가 |
