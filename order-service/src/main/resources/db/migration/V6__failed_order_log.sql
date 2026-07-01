-- ============================================================
-- V6: 실패 주문 로그 (M-3)
-- 재고 확보 실패 등으로 자동취소된 주문을 관리자가 조회하기 위한 기록.
-- 민감정보(내부 URL/스택트레이스)는 저장하지 않고 일반화된 사유만 남긴다.
-- ============================================================
CREATE TABLE failed_order_log (
    id          BIGSERIAL     PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    reason      VARCHAR(300)  NOT NULL,
    occurred_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 최근 발생 순 조회용
CREATE INDEX idx_failed_order_log_occurred_at ON failed_order_log (occurred_at);
