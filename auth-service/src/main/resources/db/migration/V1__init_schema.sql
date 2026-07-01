CREATE TABLE users (
    id         BIGSERIAL       PRIMARY KEY,
    email      VARCHAR(100)    NOT NULL UNIQUE,
    password   VARCHAR(255)    NOT NULL,
    name       VARCHAR(50)     NOT NULL,
    role       VARCHAR(20)     NOT NULL,
    created_at TIMESTAMP       NOT NULL,
    updated_at TIMESTAMP       NOT NULL
);
