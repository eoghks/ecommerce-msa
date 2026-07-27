# Order Service Overview

## 역할
주문 생성·조회·취소, 장바구니, 배송지 주소록, 배송상태 관리, 인앱 알림,
실패주문 로그를 담당하는 서비스. 재고 확보는 product-service와의 이벤트 기반 Saga(Choreography)로 처리한다.

## 기본 정보

| 항목 | 값 |
|------|----|
| 포트 | 8083 |
| DB | order_db (PostgreSQL 16) |
| Redis | 장바구니 저장, 분산락 키 보호 (`volatile-lru`) |
| Kafka | 주문/재고 이벤트 발행·구독 (Saga) |
| 의존 서비스 | gateway (라우팅·인증), product-service (재고 차감 이벤트), auth-service (사용자) |

## 주요 기술

- **JPA + PostgreSQL**: 주문·주문항목·장바구니·주소·알림·실패로그 엔티티 관리
- **Flyway**: DB 마이그레이션 (V1~V8)
- **Kafka (Saga Choreography)**: 주문 생성 → 재고 확보 → 확정/취소를 이벤트로 조율
- **Redis**: 게스트/사용자 장바구니 저장, 병합
- **Spring Security `@PreAuthorize`**: ADMIN/SELLER 권한 체크 (`X-User-Role`)
- **X-Internal-Token**: 서비스 간 구매 인증 내부 엔드포인트 보호

## 주문 Saga (Choreography)

```
주문 생성 (PENDING)
  └─▶ Kafka: order.created 발행
        └─▶ [product] 재고 차감 시도
              ├─ 성공 → Kafka: stock.decreased
              │     └─▶ [order] confirmOrder() → CONFIRMED + ORDER_CONFIRMED 알림
              └─ 실패 → Kafka: stock.decrease.failed
                    └─▶ [order] cancelOrder() → CANCELLED
                          + failed_order_log 기록(M-3) + ORDER_CANCELLED 알림
```

- 발행 토픽: `order.created`, `order.item.cancelled`
- 구독 토픽: `stock.decreased`, `stock.decrease.failed`
- 파티션 키: 주문ID / 상품ID — 동일 키 이벤트 순서 보장
- 발행 방식: `@TransactionalEventListener(AFTER_COMMIT)` → Kafka relay (ADR-001 / outbox 참조)

## 상태 축 (2개 독립)

| 축 | 값 | 전이 |
|----|----|------|
| 주문상태(OrderStatus) | PENDING → CONFIRMED / (부분)취소 | PENDING/CONFIRMED에서 전체·부분 취소 가능 |
| 배송상태(DeliveryStatus) | PREPARING → SHIPPING → DELIVERED | 전진만 허용(역행·건너뜀 불가) |

## 알림 트리거 (V1.1-4)

| 이벤트 | NotificationType |
|--------|------------------|
| 주문 확정 | ORDER_CONFIRMED |
| 주문 취소 | ORDER_CANCELLED |
| 주문 항목 취소 | ORDER_ITEM_CANCELLED |
| 배송 시작(SHIPPING) | DELIVERY_SHIPPING |
| 배송 완료(DELIVERED) | DELIVERY_DELIVERED |

> title/message는 타입별 상수 템플릿으로 조립 — 개인정보 미포함(주문번호 수준만).

## 구현 현황

### 기본
- [x] 주문 생성/조회/취소 (전체·부분 취소)
- [x] 재고 확보 Saga (Kafka choreography) + 보상 트랜잭션
- [x] 장바구니 (게스트/사용자, 병합)

### V1.1 확장
- [x] 배송지 주소록 (V1.1-3) — 저장형 주소, 기본 배송지, 주문 시 스냅샷 복사
- [x] 배송상태 관리 (V1.1-3) — PREPARING→SHIPPING→DELIVERED 전진
- [x] 인앱 알림 (V1.1-4) — 주문·배송 상태 전이 시 생성
- [x] 실패주문 로그·관리자 조회 (M-3)
- [x] 판매자 주문 조회 (본인 상품 항목만) + 항목 취소/배송상태 변경
- [x] 구매 인증 내부 엔드포인트 (`/orders/internal/purchased`, X-Internal-Token)
