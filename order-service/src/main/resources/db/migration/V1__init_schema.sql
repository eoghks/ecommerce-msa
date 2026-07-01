-- ============================================================
-- V1: orders, order_item 테이블 초기 생성
-- ============================================================

-- 주문 테이블 (order는 SQL 예약어 → orders 사용)
CREATE TABLE orders (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    total_price BIGINT          NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL
);

-- 주문자 기준 조회 (내 주문 목록)
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- 주문 상품 테이블
CREATE TABLE order_item (
    id           BIGSERIAL       PRIMARY KEY,
    order_id     BIGINT          NOT NULL,
    product_id   BIGINT          NOT NULL,
    product_name VARCHAR(200)    NOT NULL,
    price        BIGINT          NOT NULL,
    quantity     INTEGER         NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
