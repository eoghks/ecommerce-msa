-- ============================================================
-- V16: 결제 정합성 보강 (코드리뷰 C-01 / C-02 / H-02 / M-05)
-- ============================================================

-- H-02: 낙관적 락 — 동시 부분환불이 같은 cancelled_amount 를 읽고 각자 덮어써
--       잔여 취소가능액이 부풀어 초과환불되는 것(lost update)을 막는다.
ALTER TABLE payment ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- C-01: 승인 결과 재조회(GET /v1/payments/orders/{pg_order_id}) 키.
-- M-05: 결제 시도마다 새 PG 주문번호를 쓰므로(ORD-00000123-a1b2c3d4) 서버가 규칙으로 재생성할 수 없다.
--       승인 준비 시 검증한 값을 저장해 재조회·응답 대조에 사용한다. 0원 결제(PG 미사용)는 NULL.
ALTER TABLE payment ADD COLUMN pg_order_id VARCHAR(64);

-- 기존 결제는 결정적 규칙(ORD-%08d)으로 생성됐으므로 그 값으로 채운다(재조회 가능하게).
UPDATE payment
   SET pg_order_id = 'ORD-' || LPAD(order_id::text, 8, '0')
 WHERE pg_order_id IS NULL
   AND pg_payment_key IS NOT NULL;

-- C-01: status 에 UNKNOWN(승인 결과 미확정)이 추가된다.
--   READY    : 승인 요청 직전(내부 검증 통과) — PG 왕복 중
--   APPROVED : PG 승인 완료(또는 payable=0 내부 승인, §11-3)
--   CANCELED : 전액 취소·환불 완료
--   FAILED   : PG 승인 실패(주문은 CANCELLED 로 보상) 또는 고아 결제 정리
--   UNKNOWN  : 통신 실패 후 재조회로도 승인 여부를 판정 못한 상태 — 출금됐을 수 있어 조회·환불 경로에
--              남긴다. uq_payment_order_active(status <> 'FAILED')에 걸려 재결제도 막는다(이중 출금 방지).
COMMENT ON COLUMN payment.status IS 'READY/APPROVED/CANCELED/FAILED/UNKNOWN(결과 미확정)';

-- C-01/C-02: 진행 중·미확정 결제 회수 스케줄 조회용 부분 인덱스 — 대상 집합이 작다.
CREATE INDEX idx_payment_inflight ON payment (created_at) WHERE status IN ('READY', 'UNKNOWN');
