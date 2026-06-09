-- ============================================================
-- V3: product 테이블에 판매 상태 컬럼 추가 (판매 금지 기능)
-- ============================================================
ALTER TABLE product ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_product_status ON product (status);
