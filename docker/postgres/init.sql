-- 서비스별 DB 분리 (Database per Service)
-- 단일 PostgreSQL 인스턴스에 데이터베이스 3개 생성

CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE order_db;

-- 각 DB에 대한 권한은 기본 사용자(POSTGRES_USER)가 모두 보유

-- 향후 서비스별 사용자 분리 검토 [운영]
-- CREATE USER auth_user WITH PASSWORD '...';
-- GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;
