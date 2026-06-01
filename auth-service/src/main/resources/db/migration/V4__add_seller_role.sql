-- ============================================================
-- V4: role 체크 제약조건에 SELLER 추가
-- ============================================================
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'SELLER', 'ADMIN'));
