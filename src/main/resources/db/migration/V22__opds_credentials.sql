-- V22: OPDS-specific separate credentials

CREATE TABLE opds_credentials (
    user_id        CHAR(36)     PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    opds_username  VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     VARCHAR(30)  NOT NULL,
    updated_at     VARCHAR(30)  NOT NULL,
    UNIQUE (opds_username)
);
