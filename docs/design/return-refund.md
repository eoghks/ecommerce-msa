# 설계 — 반품·환불 (V1.1-5)

## 1. 목표 / 범위
배송 완료된 주문 항목에 대해 사용자가 반품을 신청하고, 판매자/관리자가 승인·거부하며, 승인 시 재고가 복구되고 환불이 처리된다.

- 포함: 반품 신청(사유 필수)·승인·거부, 승인 시 재고 복구, 반품 상태 추적, 사용자/판매자·관리자 화면
- **제외(중요)**: **실제 PG 환불 호출** — 현재 결제가 mock이라 결제 취소를 실제로 할 수 없다. 승인 시 상태를 `REFUNDED`로 전이하고 **환불 훅 지점만 남긴다**(V1.1-6 PG 연동 시 그 자리에서 실제 환불 API 호출).
- 제외(백로그): 반품 배송비 정책, 부분 수량 반품(항목 단위 전량 반품만), 교환(Exchange), 반품 상품 회수 추적.

## 2. 현황 (재사용 가능 자산)
| 자산 | 상태 |
|------|------|
| `DeliveryStatus` | PREPARING→SHIPPING→**DELIVERED** 존재 → 반품 자격 판정에 사용 |
| `OrderItemStatus` | ACTIVE / CANCELLED |
| 재고 복구 Saga | `OrderItemCancelledEvent`(AFTER_COMMIT→Kafka→product 복구, 멱등키) — **반품 승인 시 재사용** |
| 알림 | Notification(order-service 인프로세스) — 반품 상태 변경 알림에 재사용 |
| order Flyway | 최신 V8 → 신규 **V9** |

## 3. 반품 자격 (신청 조건)
- 주문의 `deliveryStatus == DELIVERED` (배송 완료된 것만)
- 대상 항목이 `OrderItemStatus.ACTIVE` (이미 취소된 항목은 반품 불가)
- 해당 주문의 **소유자 본인**만 신청 (X-User-Id)
- 동일 항목에 대해 **진행 중(REQUESTED) 또는 이미 승인/환불된 반품이 있으면 중복 신청 거부**(409)
- 거부(REJECTED)된 항목은 재신청 허용(정책 단순화 — 사유 보완 후 재요청)

> 반품 기한(예: 배송완료 후 7일)은 MVP에서 제외하고 백로그. 넣을 경우 `deliveredAt` 기록 필요(현재 미보관).

## 4. 데이터 모델 (order-service, Flyway V9)
```sql
CREATE TABLE return_request (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    order_item_id  BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,          -- 신청자(주문 소유자)
    reason         VARCHAR(300) NOT NULL,    -- 신청 사유(필수)
    status         VARCHAR(20) NOT NULL,     -- REQUESTED/APPROVED/REJECTED/REFUNDED
    reject_reason  VARCHAR(300),             -- 거부 사유
    requested_at   TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP                 -- 승인/거부 시각
);
CREATE INDEX idx_return_order      ON return_request (order_id);
CREATE INDEX idx_return_user       ON return_request (user_id, requested_at DESC);
-- 동일 항목 중복 진행 방지: 활성 상태(REQUESTED/APPROVED/REFUNDED)는 항목당 1건
CREATE UNIQUE INDEX uq_return_item_active
    ON return_request (order_item_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'REFUNDED');
```
- `ReturnStatus` enum: `REQUESTED` → `APPROVED` → `REFUNDED` / `REQUESTED` → `REJECTED`.
- 부분 유니크 인덱스가 중복 신청의 최종 방어(서비스 검증 + DB 제약 이중).

## 5. 상태 전이
```
REQUESTED --승인--> APPROVED --환불처리--> REFUNDED
     |
     +---거부---> REJECTED
```
- 전이 검증: REQUESTED에서만 승인/거부 가능. APPROVED에서만 REFUNDED. 그 외 400.
- **MVP 단순화**: 승인 시 재고 복구 + 곧바로 `REFUNDED`로 전이(mock 환불 완료 처리). PG 연동 후에는 APPROVED(환불요청)→PG 응답→REFUNDED로 분리.
  - 구현 시 `approve()` 내부에 **환불 훅 메서드**(`processRefund(...)`)를 두고, 현재는 mock 성공 처리 + 로그. V1.1-6에서 이 메서드만 실제 PG 호출로 교체.

## 6. 재고 복구
- 승인 시 대상 `OrderItem`을 취소 처리(기존 `cancelItem` 경로 재사용)하여 **`OrderItemCancelledEvent`** 발행 → product-service 재고 복구(기존 멱등키 `stock:restocked:item:{itemId}` 그대로 활용 → 중복 복구 방지).
- 이미 CANCELLED 항목은 반품 자격에서 배제되므로 이중 복구 위험 없음.
- 주문 상태는 기존 `recalculateAfterCancel()`로 PARTIALLY_CANCELLED/CANCELLED 자연 전이.

## 7. API (order-service)
| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/orders/{orderId}/items/{itemId}/returns` | 주문 소유자(USER) | 반품 신청(body: reason 필수) |
| GET | `/api/v1/returns/me` | USER | 내 반품 목록(페이징, 최신순) |
| GET | `/api/v1/returns/admin` | ADMIN, SELLER | 반품 목록 — ADMIN 전체 / SELLER는 본인 상품 포함 건만 |
| PATCH | `/api/v1/returns/{returnId}/approve` | ADMIN, 해당 SELLER | 승인(→재고 복구 + 환불 처리) |
| PATCH | `/api/v1/returns/{returnId}/reject` | ADMIN, 해당 SELLER | 거부(body: rejectReason 필수) |

- 인증: X-User-Id null → 401. 타인 주문 반품 신청/타인 반품 조회 차단(404/403).
- SELLER 권한 판정: 배송상태 변경(V1.1-3)에서 쓰는 **"주문 항목에 본인 sellerId 포함" 판정 재사용**.
- 게이트웨이: `/api/v1/returns/**`, 반품 신청 경로 인증 필수.

## 8. 알림 연계 (V1.1-4 재사용)
- 반품 신청 → (선택) 판매자/관리자 알림은 MVP 제외.
- **반품 승인/거부/환불완료 → 신청자에게 알림 생성**. `NotificationType`에 `RETURN_APPROVED`/`RETURN_REJECTED`/`RETURN_REFUNDED` 추가.

## 9. 프론트
- **내 주문 상세/목록**: 배송완료 항목에 "반품 신청" 버튼(사유 입력). 신청 후 상태 뱃지(반품접수/승인/거부/환불완료).
- **내 반품 목록**: 마이페이지에 반품 내역(상태·사유·처리시각).
- **관리자/판매자 반품 관리**: 목록 + 승인/거부(거부 사유 입력). 기존 관리자 화면 스타일·라우팅 준수.
- 실패 피드백(자격 미충족 400/중복 409/권한 403) 메시지 노출.

## 10. 레이어 / 규칙
- Controller(검증)→ReturnService(자격·전이·권한, 재고복구 이벤트, 알림)→ReturnRequestRepository.
- 전이/자격은 도메인·서비스 분기(흐름제어 예외 남용 금지). DTO record, Optional/빈컬렉션, 매직값 상수화, JPA 파라미터 바인딩(${} 금지).
- 환불 훅은 별도 메서드로 분리해 PG 연동 시 교체 지점 명확화.

## 11. 테스트
- 신청: 배송완료+ACTIVE 항목 성공 / DELIVERED 아님 400 / 이미 CANCELLED 항목 400 / 타인 주문 404 / 중복 신청 409 / 사유 누락 400.
- 승인: REQUESTED만 승인 가능(그 외 400), 승인 시 항목 CANCELLED + 재고복구 이벤트 1회 발행(중복 없음), REFUNDED 전이, 알림 생성.
- 거부: REQUESTED만 거부 가능, 거부 사유 필수, 거부 후 재신청 허용.
- 권한: ADMIN 전체 / 해당 SELLER 허용 / 타 SELLER·타 USER 403.
- 조회: 내 반품 본인만, 관리자 전체·판매자 본인 건만.

## 12. 배포
- order-service Flyway `V9`(return_request + 부분 유니크 인덱스). 실 DB 적용·재고복구·전이는 release 전 E2E로 검증.
- PG 연동(V1.1-6) 시 `processRefund` 훅을 실제 결제취소로 교체 — 그때 반품 상태 분리(APPROVED→REFUNDED 비동기) 재검토.
