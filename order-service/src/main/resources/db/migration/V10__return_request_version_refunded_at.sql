-- ============================================================
-- V10: 반품 요청 동시성·감사 보강 (코드리뷰 M-1 / M-4)
-- ============================================================

-- M-1: 낙관적 락 버전 컬럼 — 동시 승인/거부 경쟁 시 뒤늦은 트랜잭션을 충돌(409)로 실패시킨다.
ALTER TABLE return_request
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- M-4: 환불 확정 시각 분리 — processed_at(승인/거부 시각)이 환불 시각으로 덮이지 않도록 한다.
ALTER TABLE return_request
    ADD COLUMN refunded_at TIMESTAMP;

-- 기존 환불 완료 건은 승인/환불이 같은 시각에 처리됐으므로 processed_at 을 그대로 승계한다.
UPDATE return_request
   SET refunded_at = processed_at
 WHERE status = 'REFUNDED';
