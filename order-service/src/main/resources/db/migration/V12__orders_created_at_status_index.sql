-- ============================================================
-- V12: 관리자 매출 통계(V1.1-7) 집계 인덱스 최적화 (M-4)
-- 통계 5개 쿼리는 모두 created_at 범위 + status 조건(IN / <>)으로 필터하므로
-- (created_at, status) 복합 인덱스로 범위 스캔 후 status 를 인덱스 안에서 걸러낸다.
--
-- 선행 컬럼 선택 근거: 조회 기간(최대 366일)이 항상 지정되는 반면 status 조건은
-- 쿼리마다 다르고(IN 2건 / <> 1건) 부등호 조건도 있어 status 를 선행에 두면
-- 부등호 쿼리에서 인덱스 활용이 떨어진다. 따라서 created_at 을 선행으로 둔다.
--
-- V11 의 단일 인덱스 idx_orders_created_at 은 이 복합 인덱스의 선행 컬럼과 동일해
-- 완전히 중복(prefix)이므로 제거한다 — 쓰기 비용·저장공간 절감.
--
-- 운영 주의: CREATE INDEX 는 orders 쓰기를 차단하므로 대용량 운영 DB 에서는
-- CREATE INDEX CONCURRENTLY 로 별도 적용한다(배포 체크리스트 8번 참고).
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_orders_created_at_status ON orders (created_at, status);

DROP INDEX IF EXISTS idx_orders_created_at;
