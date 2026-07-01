-- ============================================================
-- V1: category, product 테이블 초기 생성
-- ============================================================

-- 카테고리 테이블
CREATE TABLE category (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE INDEX idx_category_name ON category (name);

-- 상품 테이블
CREATE TABLE product (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL,
    description VARCHAR(1000),
    price       BIGINT          NOT NULL,
    stock       INTEGER         NOT NULL DEFAULT 0,
    image_url   VARCHAR(500),
    category_id BIGINT          NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE INDEX idx_product_category_id  ON product (category_id);
