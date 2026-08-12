-- ============================================================
-- V11: 관리자 매출 통계(V1.1-7) 기간 조회 인덱스
-- 통계 집계는 orders.created_at 범위 조건으로 스캔하므로 인덱스를 추가한다.
-- (order_item(order_id)·failed_order_log(occurred_at) 인덱스는 V1·V6에 이미 존재)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);
