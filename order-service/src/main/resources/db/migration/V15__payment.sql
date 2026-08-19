-- ============================================================
-- V15: PG 결제 (payment-foundation §5, §7, §11-1)
-- ============================================================

-- 결제 1건 = 주문 1건. 승인 전 READY 로 만들고 PG 응답에 따라 APPROVED/FAILED 로 확정한다.
--   READY    : 승인 요청 직전(내부 검증 통과) — PG 왕복 중
--   APPROVED : PG 승인 완료(또는 payable=0 내부 승인, §11-3)
--   CANCELED : 전액 취소·환불 완료
--   FAILED   : PG 승인 실패(주문은 CANCELLED 로 보상)
CREATE TABLE payment (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    pg_provider      VARCHAR(20)  NOT NULL,   -- TOSS / NONE(0원 결제)
    pg_payment_key   VARCHAR(200),            -- PG 거래키 — 0원 결제는 NULL
    amount           BIGINT       NOT NULL,   -- 승인 금액(= orders.payable_amount)
    cancelled_amount BIGINT       NOT NULL DEFAULT 0,  -- 누적 취소·환불 금액(부분취소 포함)
    status           VARCHAR(20)  NOT NULL,
    approved_at      TIMESTAMP,
    canceled_at      TIMESTAMP,
    fail_reason      VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- 주문당 승인 1건 보장 (§8) — 실패 건은 재시도 이력으로 남을 수 있어 제외한다.
CREATE UNIQUE INDEX uq_payment_order_active ON payment (order_id) WHERE status <> 'FAILED';

-- PG 거래키 멱등 (§8) — 웹훅·재전송으로 같은 결제가 두 건이 되지 않게 한다.
CREATE UNIQUE INDEX uq_payment_pg_key ON payment (pg_payment_key) WHERE pg_payment_key IS NOT NULL;

CREATE INDEX idx_payment_order_id ON payment (order_id);

-- 미완료 주문 만료 스케줄 조회용 부분 인덱스 (§11.1) — 결제 대기 주문만 대상이라 집합이 작다.
CREATE INDEX idx_orders_payment_pending ON orders (created_at) WHERE status = 'PAYMENT_PENDING';
