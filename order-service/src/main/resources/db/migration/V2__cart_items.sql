-- ============================================================
-- V2: cart_items 테이블 — 로그인 사용자 장바구니 영구 저장
-- 비로그인 사용자는 Redis (cart:guest:{guestId}, TTL 30일)
-- ============================================================

CREATE TABLE cart_items (
    id           BIGSERIAL       PRIMARY KEY,
    user_id      BIGINT          NOT NULL,
    product_id   BIGINT          NOT NULL,
    product_name VARCHAR(200)    NOT NULL,
    price        BIGINT          NOT NULL,
    quantity     INTEGER         NOT NULL,
    image_url    VARCHAR(500),
    created_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    -- 동일 유저가 같은 상품을 중복 추가하면 수량만 증가
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_cart_items_user_id ON cart_items (user_id);
