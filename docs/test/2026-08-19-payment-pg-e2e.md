# PG 결제(V1.1-6) E2E + UI 실검증

> 대상: `feature/payment-pg` — 토스페이먼츠 샌드박스 연동
> 환경: 실 서버(gateway 8080 / auth 8081 / product 8082 / order 8083 / frontend 5173) + Docker 인프라
> 판정: ✅ PASS / ❌ FAIL / ⚠️ 환경한계 / ➖ 미검증

## 0. 마이그레이션 (릴리스 게이트)

| # | 항목 | 결과 | 근거 |
|---|------|------|------|
| 0-1 | **V15** payment 테이블 | ✅ | 15개 컬럼 생성(order_id·pg_payment_key·amount·cancelled_amount·status·approved_at·canceled_at·fail_reason 등) |
| 0-2 | **V16** payment integrity | ✅ | `version`(낙관적 락)·`pg_order_id` 추가 + 백필 |
| 0-3 | 유니크·인덱스 | ✅ | `uq_payment_order_active`(FAILED 제외 → abandon 시 재결제 허용), `uq_payment_pg_key`, `idx_payment_inflight`(READY/UNKNOWN) |
| 0-4 | `PaymentStatus`에 UNKNOWN | ✅ | C-01 대응 — 승인 결과 미확정 건을 환불·조회 경로에서 배제하지 않음 |

## 1. 핵심 상태 흐름 (설계 §5, §11-2)

| # | 항목 | 기대 | 실측 | 결과 |
|---|------|------|------|------|
| 1-1 | 주문 생성 상태 | PAYMENT_PENDING | `PAYMENT_PENDING` (주문 #54) | ✅ |
| 1-2 | **승인 전 재고 미차감** | 재고 불변 | 상품 57 재고 **49 → 49 유지** | ✅ |
| 1-3 | 금액 스냅샷 | itemsTotal=payable=100 | `itemsTotal:100, payableAmount:100, couponDiscount:0, mileageUsed:0` | ✅ |

> **1-2가 이번 변경의 핵심**이다. 기존에는 주문 생성 즉시 재고를 차감했는데, 결제 승인 이후로 옮겨 "결제 실패했는데 재고가 빠진" 상태를 원천 차단했다.

## 2. 결제 보안 (돈이 오가는 경로)

| # | 공격 시도 | 기대 | 실측 | 결과 |
|---|-----------|------|------|------|
| 2-1 | **금액 위변조** (payable=100인데 99999 전송) | 거부 | **400** `"결제 요청 금액이 주문 금액과 일치하지 않습니다. orderId=54"` | ✅ |
| 2-2 | **다른 주문의 pgOrderId** 사용 | 거부 | **400** `"PG 주문번호가 주문과 일치하지 않습니다. orderId=54"` | ✅ |
| 2-3 | **타인 주문 승인** (주문 20은 타 사용자) | 거부 | **404** (존재 노출 방지) | ✅ |
| 2-4 | 미인증 승인 | 401 | **401** | ✅ |
| 2-5 | 필수값 누락(pgOrderId) | 400 | **400** + 필드별 메시지 | ✅ |

> 클라이언트가 보낸 `amount`는 **대조용**이고 실제 승인 금액은 서버가 주문에서 계산한 `payableAmount`를 사용한다. 위변조 경로가 닫혀 있다.

## 3. 코드리뷰 CRITICAL 2건 — 수정 구조 확인

### C-01 승인 타임아웃을 확정 실패로 단정 (돈 유실 위험)
- **문제**: read timeout(10s)을 실패로 확정해 결제 FAILED + 주문 CANCELLED. 토스는 이미 출금했을 수 있고, FAILED는 조회에서 제외돼 **환불 경로가 이 건을 영원히 못 찾음**.
- **수정 확인**: 통신 실패(`PaymentGatewayUnavailableException`)와 PG 거절을 분리 → `GET /v1/payments/orders/{pgOrderId}` **재조회로 판정**. `DONE`이면 정상 확정, 명확한 실패면 FAILED, **판정 불가면 `UNKNOWN`** 으로 남기고 `failed_order_log`(REQUIRES_NEW) 기록. UNKNOWN은 `findActiveByOrderId`에 **포함**되어 환불 경로에서 배제되지 않음(스키마·enum 실측 확인).

### C-02 승인 성공 후 확정 실패 + 재결제 영구 차단
- **문제**: PG 왕복 중 30분 만료 → `markPaid()` 예외 → 돈은 승인, 주문은 CANCELLED, READY 고아가 유니크를 점유해 재결제 409 영구 차단.
- **수정 확인**:
  - 만료 조회·조건부 UPDATE에 **`NOT EXISTS(READY/UNKNOWN 결제)`** 조건 — 실 DB에서 만료 대상 쿼리 실행해 0건 확인
  - `markApproved` 실패 시 **승인분 PG 취소 보상**(멱등키 `PAYCMP-{id}`), 취소도 실패하면 UNKNOWN + 미결 기록
  - 고아 진행 중 결제는 **`abandon()`**(결제만 FAILED, 주문은 결제 대기 유지) → `uq_payment_order_active`가 FAILED를 제외하므로 **재결제 가능**(인덱스 정의 실측 확인)
  - 5분 주기 회수 스케줄러 + 승인 요청 시점 두 곳에서 실행

## 4. UI 실검증

| # | 항목 | 결과 | 근거 |
|---|------|------|------|
| 4-1 | 내 주문 "결제 대기" 뱃지 | ✅ | 주문 #54에 뱃지 + **"결제 예정금액 ₩100"**(확정 금액과 구분된 문구) |
| 4-2 | "결제 계속하기" 링크 | ✅ | `/payments/54`로 연결 |
| 4-3 | 결제 페이지 렌더 | ✅ | 주문번호·금액·상품 표시, **"30분 안에 결제하지 않으면 주문이 자동 취소됩니다"** 안내, "나중에"·"₩100 결제하기" 버튼 |
| 4-4 | **토스 SDK 로드** | ✅ | `typeof window.TossPayments === 'function'`, 위젯 iframe 2개 삽입(payment-methods·agreement) |
| 4-5 | 앱 API 호출 | ✅ | `/api/v1/orders/54` 등 전부 200 |
| 4-6 | **결제위젯 실제 렌더** | ⚠️ **환경한계** | iframe 높이 0 + `ERR_NAME_NOT_RESOLVED` — **내장 브라우저 샌드박스가 `payment-widget.tosspayments.com` 내부 리소스를 차단**. 코드 문제 아님(SDK 로드·iframe 삽입까지 정상). MinIO 이미지 때와 동일 패턴 |

## 5. 미검증 (정직 기록)
- **실제 카드 결제 승인 흐름** — 위 4-6 때문에 결제창을 띄울 수 없어 **승인 성공 → PENDING 전이 → 재고 차감 → 완료 화면**을 UI로 끝까지 못 갔다. 승인 API 자체는 단위테스트(모킹)와 보안 검증으로 커버.
  - **→ 실제 크롬에서 한 번 확인 필요**(테스트 카드로 결제 후 주문 상태·재고 확인)
- **환불 경로 실호출** — 토스 API 실호출이 필요해 미검증(단위테스트 모킹으로 커버). 반품 승인·재고 실패 보상의 PG 취소 호출.
- **만료 스케줄러 실제 실행** — 5분/시간 주기라 관측 시점에 미도래. 조회 조건은 실 DB로 검증.

## 6. 단위테스트
- `:order-service:test` **전체 371건 / 실패 0건** (결제 서브셋 221건 포함, Testcontainers 실 DB)
- 프론트 `tsc --noEmit` 0에러, `npm run build` 성공, lint 통과

## 결론
결제 도입의 **핵심 안전장치 3가지가 실측 확인**됐다 — ① 승인 전 재고 미차감, ② 금액 위변조·소유권·주문번호 방어, ③ 승인 결과 미확정(UNKNOWN)·고아 결제(abandon) 처리로 돈이 추적 불가해지거나 재결제가 막히는 경로 제거.
**남은 것은 결제창 실물 흐름 1건**으로, 내장 브라우저의 외부 도메인 차단 때문이며 실제 크롬에서 확인이 필요하다.
