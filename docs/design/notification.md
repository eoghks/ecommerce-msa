# 설계 — 알림 (주문·배송) (V1.1-4)

## 1. 목표 / 범위
주문·배송 상태가 바뀔 때 사용자에게 인앱 알림을 남기고, Navbar 뱃지·목록으로 확인·읽음 처리한다.

- 포함: 알림 생성(주문 확정/취소/항목취소/배송상태 변경), 내 알림 목록·미읽음 수·읽음 처리, Navbar 뱃지·목록
- 제외(백로그): 이메일/푸시/SSE 실시간, 운영자(ADMIN) 알림 채널, 독립 notification-service

## 2. 배치 결정 — order-service 확장 (신규 서비스 아님)
| 후보 | 판단 |
|------|------|
| **order-service 확장 (채택)** | 알림 트리거가 전부 주문 라이프사이클이라 order-service가 시점을 이미 앎 → 인프로세스 생성. 인프라 추가 최소. |
| 독립 notification-service | order-service가 order.confirmed/cancelled/delivery-changed 이벤트를 새로 발행해야 하고 새 모듈·DB·컨슈머·게이트웨이·CI 필요. 현재 이득 대비 과함. |

**트레이드오프**: 알림이 order-service에 결합. 알림 소스가 주문 밖(상품 재입고, 프로모션 등)으로 확장되면 그때 이벤트 기반 독립 서비스로 분리(→ 백로그). 지금은 인프로세스가 합리적.

## 3. 트리거 지점 (order-service 내부, 상태 전이 시 인프로세스 생성)
| 이벤트 | 위치 | 알림 타입 |
|--------|------|-----------|
| 주문 확정(재고 차감 성공) | `StockEventConsumer`(stock.decreased 처리) | ORDER_CONFIRMED |
| 주문 취소(재고부족 자동취소) | `StockEventConsumer`(stock.decrease.failed) | ORDER_CANCELLED |
| 사용자/판매자/관리자 취소 | `OrderService.cancelByUser` / `cancelOrderItem` | ORDER_CANCELLED / ORDER_ITEM_CANCELLED |
| 배송 상태 변경 | `OrderService.updateDeliveryStatus` | DELIVERY_SHIPPING / DELIVERY_DELIVERED |

- 알림은 주문 소유자(`order.getUserId()`) 대상.
- 생성은 상태 전이 트랜잭션과 **같은 트랜잭션**(동일 서비스 DB 쓰기 — 원자성). 컨슈머 경로도 처리 트랜잭션 내에서.
- 실패해도 주문 처리 자체를 막지 않도록 주의(알림 저장 예외가 상태 전이 롤백시키지 않게 — 단, 같은 트랜잭션이면 함께 롤백됨. MVP는 같은 트랜잭션 허용, 알림 저장은 단순 insert라 실패 위험 낮음).

## 4. 데이터 모델 (order-service, Flyway V8)
```sql
CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    order_id    BIGINT,          -- 관련 주문(클릭 시 이동)
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE is_read = false;
```
- `type`은 enum(NotificationType) 문자열 저장. title/message는 서버에서 타입별 상수 템플릿으로 조립(개인정보 최소, 주문번호 수준만).

## 5. API (`/api/v1/notifications`, 인증 필요)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/notifications/me` | 내 알림 목록(페이징, 최신순) |
| GET | `/api/v1/notifications/me/unread-count` | 미읽음 개수(뱃지용, 경량) |
| PATCH | `/api/v1/notifications/{id}/read` | 단건 읽음(본인) |
| PATCH | `/api/v1/notifications/read-all` | 전체 읽음(본인) |

- X-User-Id null→401, 타인 알림 읽음 처리 차단(404). 목록/카운트는 본인 것만.
- 게이트웨이 `/api/v1/notifications/**` 인증 필수 경로 추가.

## 6. 프론트
- **Navbar 종 아이콘 + 미읽음 뱃지**: `unread-count`를 주기 폴링(예 30s) 또는 라우팅 시 갱신. 로그인 시에만.
- **알림 드롭다운/목록**: 최신 목록, 항목 클릭 시 읽음 처리 + 관련 주문으로 이동(orderId 있으면).
- "모두 읽음" 버튼.
- 로그아웃 시 폴링 중단·상태 초기화.

## 7. 레이어 / 규칙
- Controller(검증)→NotificationService(생성/조회/읽음)→NotificationRepository.
- 상태 전이 지점(OrderService/StockEventConsumer)에서 NotificationService 호출로 생성(도메인 로직과 분리).
- DTO record, Optional/빈컬렉션, 매직값·메시지 템플릿 상수화. JPA 파라미터 바인딩(${} 금지). 개인정보/토큰 로그 금지.
- 메시지 템플릿은 타입별 상수(예: ORDER_CONFIRMED → "주문이 확정되었습니다. (주문 #{id})").

## 8. 테스트
- 생성: 확정/취소/항목취소/배송상태 변경 시 해당 userId·type 알림 1건 생성(전이 지점별).
- 조회: 본인 목록 페이징·미읽음 수·타인 알림 격리.
- 읽음: 단건/전체 읽음 반영, 타인 알림 읽음 차단, 미읽음 수 감소.
- 인증: userId null 401.

## 9. 배포
- order-service Flyway `V8`(notification 테이블). 실 DB 적용·전이별 생성은 release 전 E2E로 검증.
- 게이트웨이 알림 라우팅 확인.

## 10. 향후 (백로그)
- 독립 notification-service + Kafka 이벤트 구독(주문 밖 소스 확장 시).
- 이메일/푸시/SSE 실시간, 운영자(ADMIN) 알림 채널(M-3 실시간 알림 정식화).
