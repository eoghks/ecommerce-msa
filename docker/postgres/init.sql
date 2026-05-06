-- 외부 접속용 비밀번호 설정 (scram-sha-256 인증)
ALTER USER eoghks WITH PASSWORD 'eoghks_local';

-- auth-service DB
CREATE DATABASE auth_db;

-- product-service DB
CREATE DATABASE product_db;

-- order-service DB
CREATE DATABASE order_db;
