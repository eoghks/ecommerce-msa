-- ============================================================
-- V13: 주문 금액 모델 확장 + 구매확정 (payment-foundation §1, §3.2)
-- ============================================================

-- ── 금액 모델 (§1) ─────────────────────────────────────────
-- 결제·쿠폰·마일리지 도입 전이라 지금은 할인이 없다.
-- 불변식: payable_amount = items_total - coupon_discount - mileage_used (>= 0)
-- 기존 total_price 는 "ACTIVE 항목 정가 합계"로 유지되며 items_total 과 같은 값을 가진다.
ALTER TABLE orders
    ADD COLUMN items_total     BIGINT NOT NULL DEFAULT 0,   -- 항목 정가 합계
    ADD COLUMN coupon_discount BIGINT NOT NULL DEFAULT 0,   -- 쿠폰 할인액 (V1.1-10)
    ADD COLUMN mileage_used    BIGINT NOT NULL DEFAULT 0,   -- 마일리지 사용액 (V1.1-9)
    ADD COLUMN payable_amount  BIGINT NOT NULL DEFAULT 0,   -- 실 결제금액
    ADD COLUMN mileage_earned  BIGINT NOT NULL DEFAULT 0;   -- 적립 마일리지 (구매확정 시점 적립)

-- 기존 주문 마이그레이션 — 할인이 없었으므로 정가 합계 = 실결제액 (§9-1)
UPDATE orders
   SET items_total    = total_price,
       payable_amount = total_price;

-- ── 구매확정 (§3.2) ───────────────────────────────────────
-- delivered_at: 배송완료(DELIVERED) 전이 시각. 자동확정 기준일(배송완료 + N일) 계산에 필요하다.
-- purchase_confirmed_at: 구매확정 시각. NULL 이면 미확정(= 반품 가능 구간).
ALTER TABLE orders
    ADD COLUMN delivered_at          TIMESTAMP,
    ADD COLUMN purchase_confirmed_at TIMESTAMP;

-- 기존 배송완료 주문 백필 — 전이 시각을 별도로 기록한 적이 없으므로 최종 수정시각으로 근사한다.
-- (배송완료가 마지막 상태 변경인 경우가 대부분이라 실제 전이 시각에 가장 가깝다)
UPDATE orders
   SET delivered_at = updated_at
 WHERE delivery_status = 'DELIVERED';

-- 자동확정 스케줄러 조회용 부분 인덱스 — 미확정 배송완료 주문만 대상이라 대상 집합이 매우 작다.
CREATE INDEX idx_orders_auto_confirm
    ON orders (delivered_at)
    WHERE purchase_confirmed_at IS NULL AND delivery_status = 'DELIVERED';
