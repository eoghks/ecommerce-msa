-- ============================================================
-- V9: 상품 리뷰·별점 (V1.1-1)
-- 구매한 상품에 별점(1~5)과 텍스트 리뷰를 남기고, 상품에 평균 별점/개수를 비정규화한다.
-- ============================================================
CREATE TABLE review (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,
    CONSTRAINT uk_review_user_product UNIQUE (user_id, product_id)
);

-- 상품별 리뷰 목록 조회(product_id, 최신순) 최적화
CREATE INDEX idx_review_product ON review (product_id, created_at DESC);

-- 상품 평균 별점/개수 비정규화 (핫 리드 경로 — 상품 조회 시 재계산 없이 노출)
ALTER TABLE product ADD COLUMN rating_avg   NUMERIC(2,1) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN rating_count INT          NOT NULL DEFAULT 0;
