-- ============================================================
-- V14: 구매확정 백필 보정 + 금액 불변식 제약 (코드리뷰 H-4, M-2, M-7)
-- V13 은 이미 적용된 이력이 있어 체크섬 충돌을 피하려고 수정하지 않고 보정 마이그레이션으로 처리한다.
-- ============================================================

-- ── H-4: 배포 직후 과거 주문 일괄 자동확정 방지 ────────────────
-- V13 은 기존 배송완료 주문의 delivered_at 을 updated_at(과거 시각)으로 백필했다.
-- 그 결과 배포 후 첫 스케줄 실행(매시 정각)에서 기준일(기본 7일)이 이미 지난 주문이 한꺼번에
-- 자동확정되어 (a) 과거 주문에 뜬금없는 확정 알림이 대량 발송되고
-- (b) 반품을 준비 중이던 사용자의 반품 자격이 배포 즉시 사라진다.
--
-- 대응: 미확정 배송완료 주문의 기준 시각을 마이그레이션 시각으로 재설정해
--       기존 사용자에게 배포 시점부터 유예기간(기본 7일)을 그대로 보장한다.
--       "이미 확정된 것으로 처리"하는 대안보다 사용자의 반품 권리를 지키는 쪽을 택했다.
--
-- 주의(M-7): 이 값은 실제 배송완료 시각이 아니라 "자동확정 기준 시각"이다.
--   V13 백필의 updated_at 근사도 항목취소·반품승인 등 배송완료 이후 변경으로 갱신되므로
--   실제 배송일과 다를 수 있었다. 정확한 배송완료 시각이 필요하면
--   notifications 의 DELIVERY_DELIVERED 알림 created_at 을 참고해야 한다(운영 문의 대비 기록).
--   V13 이후 배송완료된 주문은 delivered_at 이 실제 전이 시각으로 기록되지만,
--   여기서는 이들도 함께 미뤄진다 — 유예가 늘어날 뿐 사용자 권리를 뺏지 않는 방향이라 허용한다.
UPDATE orders
   SET delivered_at = now()
 WHERE delivery_status = 'DELIVERED'
   AND purchase_confirmed_at IS NULL
   AND delivered_at IS NOT NULL;

-- ── M-2: 금액 불변식(payment-foundation §1)을 스키마로 보호 ─────
-- 애플리케이션(Order.applyAmounts) 밖의 배치·수기 SQL 이 금액 컬럼을 직접 건드려도
-- payable_amount = items_total - coupon_discount - mileage_used (>= 0) 이 깨지지 않게 한다.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_payable_amount
    CHECK (payable_amount = items_total - coupon_discount - mileage_used
           AND payable_amount >= 0);
