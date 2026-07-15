# 설계 — 배송지 관리 + 배송 상태 (V1.1-3)

## 1. 목표 / 범위
사용자가 배송지를 주소록으로 저장·관리하고 주문 시 선택하며, 주문의 배송 진행 상태를 추적한다.

- 포함: 주소록 CRUD(기본 배송지), 주문 시 주소록 선택(스냅샷 저장), 주문 배송상태(준비중→배송중→배송완료) + 상태 변경 API + 화면 표시
- 제외(백로그): 택배사 연동/송장번호 실시간 추적, 배송비 계산, 도서산간 처리

## 2. 현황 (중요)
- Order 엔티티에 **이미 `receiver`, `phone`, `address` 스냅샷 필드 존재**(주문 생성 시 `OrderCreateRequest`로 입력, `V3__add_shipping_to_orders`). → 배송지 "스냅샷"은 이미 구현돼 있음.
- **없는 것**: (1) 저장형 주소록(매번 타이핑) (2) 배송 상태 필드.
- OrderStatus: PENDING / CONFIRMED / PARTIALLY_CANCELLED / CANCELLED (주문 라이프사이클). 배송상태와는 **별도 축**.
- 배치: 주소록·배송상태 모두 **order-service**(배송 정보/주문 소유). 사용자 식별은 `X-User-Id`.
- order Flyway 다음 버전 **V7**.

## 3. 주소록 (Address)
### 3.1 데이터 (order-service, Flyway V7 일부)
```sql
CREATE TABLE address (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    receiver    VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    address     VARCHAR(300) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);
CREATE INDEX idx_address_user ON address (user_id);
```
- 기본 배송지는 사용자당 최대 1개(새 기본 지정 시 기존 기본 해제 — 서비스에서 보장).

### 3.2 API (`/api/v1/addresses`, 인증 필요)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/addresses` | 내 주소록 목록(기본 우선) |
| POST | `/api/v1/addresses` | 추가(첫 주소는 자동 기본) |
| PUT | `/api/v1/addresses/{id}` | 수정(본인) |
| DELETE | `/api/v1/addresses/{id}` | 삭제(본인) |
| PATCH | `/api/v1/addresses/{id}/default` | 기본 배송지 지정(기존 기본 해제) |

- 전부 `X-User-Id` 기준 본인 소유만(IDOR 방지). null → 401, 타인 소유 → 404.
- 검증: receiver/phone/address 길이·공백(Controller 진입), phone 형식(기존 규칙 따름).

## 4. 주문 시 배송지 사용 (스냅샷 유지)
- `OrderCreateRequest`에 **선택적 `addressId`** 추가.
  - `addressId` 있으면: 해당 주소(본인 소유 검증) 값을 receiver/phone/address로 **복사(스냅샷)** 하여 주문에 저장.
  - `addressId` 없으면: 기존처럼 receiver/phone/address 직접 입력(하위호환 유지).
  - 둘 다 없거나 유효하지 않으면 400.
- 스냅샷이므로 이후 주소록을 수정/삭제해도 **주문 이력의 배송지는 보존**(현행 필드 그대로 활용 — 추가 작업 최소).
- 프론트: 주문 화면에서 주소록 드롭다운 선택 → 폼 자동 채움(직접 수정도 허용).

## 5. 배송 상태 (deliveryStatus)
### 5.1 모델
- `DeliveryStatus` enum: `PREPARING`(준비중) → `SHIPPING`(배송중) → `DELIVERED`(배송완료). **전진만**(되돌리기 불가).
- Order에 `deliveryStatus` 컬럼 추가(V7). 기본값 `PREPARING`.
- 배송상태는 **CONFIRMED/PARTIALLY_CANCELLED 주문에서만 의미**. PENDING(미확정)·CANCELLED(취소) 주문은 배송상태 변경 대상 아님(400).
- OrderStatus(주문 취소 등)와 독립. 취소된 주문은 배송상태를 진행시키지 않는다.

### 5.2 상태 변경 API
| 메서드 | 경로 | 권한 |
|--------|------|------|
| PATCH | `/api/v1/orders/{orderId}/delivery-status` (body: 다음 상태) | ADMIN, 또는 해당 주문에 자기 상품이 포함된 SELLER |

- **전이 검증**: PREPARING→SHIPPING→DELIVERED 순서만. 역행/건너뜀/동일 상태 재설정 → 400.
- 권한: ADMIN 전체. SELLER는 **주문 항목 중 본인(sellerId) 상품이 있는 주문**만(기존 판매자 주문 조회 PR-A 소유 판정 재사용).
- **다중 판매자 캐비앳**: 한 주문에 여러 판매자 상품이 섞이면 배송상태는 주문 단위 1개다. MVP는 "주문에 자기 상품이 있는 판매자면 진행 가능"으로 단순화(포트폴리오 범위). 판매자별 부분배송은 백로그.
- 상태 변경 시 (선택) 이벤트 발행 지점 표시 — V1.1-4 알림에서 구독할 수 있게 훅만 남기되 MVP는 알림 미구현.

### 5.3 조회
- 주문 목록/상세 응답(OrderResponse 등)에 `deliveryStatus` 포함. 사용자 내 주문·판매자/관리자 주문관리 화면에 표시.

## 6. 프론트
- **주소록 관리 화면**(마이페이지): 목록/추가/수정/삭제/기본 지정.
- **주문 화면**: 주소록 선택 드롭다운(+직접 입력 폴백), 기본 배송지 자동 선택.
- **주문 상세/목록**: 배송상태 뱃지(준비중/배송중/배송완료).
- **판매자/관리자 주문관리**: 배송상태 변경 컨트롤(전이 가능한 다음 상태만 노출).

## 7. 레이어 / 규칙
- Controller(검증)→Service(소유·전이 검증, 기본배송지 유일성)→Repository. DTO record, Optional/빈컬렉션, 매직값 상수화. JPA 파라미터 바인딩(${} 금지).
- 배송상태 전이·기본배송지 유일성은 도메인/서비스 분기로(예외 남용 금지).

## 8. 테스트
- 주소록: CRUD·본인 격리·기본배송지 유일성(새 기본 지정 시 기존 해제)·첫 주소 자동 기본·타인 접근 404.
- 주문 스냅샷: addressId로 생성 시 값 복사·주소 수정/삭제 후 주문 배송지 불변·유효하지 않은 addressId 400·직접입력 하위호환.
- 배송상태: 전이 정상(P→S→D)·역행/건너뜀 400·PENDING/CANCELLED 변경 400·권한(ADMIN/해당 SELLER 허용, 타 SELLER·USER 403).

## 9. 배포
- order-service Flyway `V7`(address 테이블 + orders.delivery_status 컬럼). 실 DB 적용·전이·스냅샷은 release 전 E2E로 검증.
- 신규 인증 경로(`/api/v1/addresses/**`, `/orders/{id}/delivery-status`) 게이트웨이 라우팅 확인.
