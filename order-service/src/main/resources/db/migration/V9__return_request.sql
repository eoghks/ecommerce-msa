-- ============================================================
-- V9: 반품 요청(return_request) 테이블 — 반품·환불 (V1.1-5)
-- ============================================================

-- 배송 완료된 주문 항목에 대한 반품 신청. status 는 ReturnStatus enum 문자열 저장.
-- 항목 단위 전량 반품만 지원(부분 수량 반품은 백로그).
CREATE TABLE return_request (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    order_item_id  BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,          -- 신청자(주문 소유자)
    reason         VARCHAR(300) NOT NULL,    -- 신청 사유(필수)
    status         VARCHAR(20) NOT NULL,     -- REQUESTED/APPROVED/REJECTED/REFUNDED
    reject_reason  VARCHAR(300),             -- 거부 사유
    requested_at   TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP                 -- 승인/거부 시각
);

-- 주문 기준 조회용
CREATE INDEX idx_return_order ON return_request (order_id);
-- 내 반품 목록(최신순) 조회용
CREATE INDEX idx_return_user  ON return_request (user_id, requested_at DESC);
-- 동일 항목 중복 진행 방지: 활성 상태(REQUESTED/APPROVED/REFUNDED)는 항목당 1건
-- 거부(REJECTED)는 제외 → 사유 보완 후 재신청 허용
CREATE UNIQUE INDEX uq_return_item_active
    ON return_request (order_item_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'REFUNDED');
