-- ============================================================
-- V5: order_item 에 취소 사유·취소 시각 컬럼 추가 (항목 단위 취소)
-- ============================================================
ALTER TABLE order_item ADD COLUMN cancel_reason VARCHAR(300);
ALTER TABLE order_item ADD COLUMN cancelled_at  TIMESTAMP;
