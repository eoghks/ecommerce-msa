-- ============================================================
-- V2: product 테이블에 판매자 ID 컬럼 추가
-- ============================================================
ALTER TABLE product ADD COLUMN seller_id BIGINT;

CREATE INDEX idx_product_seller_id ON product (seller_id);
