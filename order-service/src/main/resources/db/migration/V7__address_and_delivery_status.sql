-- ============================================================
-- V7: 배송지 주소록(address) 테이블 + 주문 배송상태 컬럼 (V1.1-3)
-- ============================================================

-- 저장형 배송지 주소록
CREATE TABLE address (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    receiver    VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    address     VARCHAR(300) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);
CREATE INDEX idx_address_user ON address (user_id);
-- 기본 배송지 유일성 최종 방어: 사용자당 is_default=true 행은 최대 1개 (PostgreSQL 부분 유니크 인덱스).
-- 서비스의 read-then-write 로직과 별개로 동시 요청 시에도 기본 배송지가 2개가 되는 것을 DB가 차단한다.
CREATE UNIQUE INDEX uq_address_user_default ON address (user_id) WHERE is_default;

-- 주문 배송상태 (준비중→배송중→배송완료). OrderStatus 와는 별도 축.
ALTER TABLE orders
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PREPARING';
