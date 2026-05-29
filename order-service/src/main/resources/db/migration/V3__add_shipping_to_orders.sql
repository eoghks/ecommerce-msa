-- ============================================================
-- V3: orders 테이블에 배송 정보 컬럼 추가 (HR-05)
-- ============================================================
ALTER TABLE orders
    ADD COLUMN receiver VARCHAR(100),
    ADD COLUMN phone    VARCHAR(20),
    ADD COLUMN address  VARCHAR(300);
