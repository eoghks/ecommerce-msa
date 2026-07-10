-- ============================================================
-- V7: 위시리스트(찜) 테이블 (V1.1-2)
-- 로그인 사용자가 상품을 찜하고 마이페이지에서 조회한다.
-- ============================================================
CREATE TABLE wishlist (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id)
);

-- 내 찜 목록 조회(user_id, 최신순) 최적화
CREATE INDEX idx_wishlist_user ON wishlist (user_id, created_at DESC);
