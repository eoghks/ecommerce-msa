-- ============================================================
-- V4: order_item 에 판매자 ID·상태 컬럼 추가 (판매자/관리자 주문 관리)
-- ============================================================
ALTER TABLE order_item ADD COLUMN seller_id BIGINT;
ALTER TABLE order_item ADD COLUMN status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_order_item_seller_id ON order_item (seller_id);
