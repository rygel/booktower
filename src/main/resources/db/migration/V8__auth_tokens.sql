-- Password reset tokens (self-hosted: token URL is logged / shown to admin)
CREATE TABLE password_reset_tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at VARCHAR(30)  NOT NULL,
    used_at    VARCHAR(30),
    created_at VARCHAR(30)  NOT NULL,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_prt_token  ON password_reset_tokens(token_hash);
CREATE INDEX idx_prt_user   ON password_reset_tokens(user_id);

-- Programmatic API tokens (used by OPDS and future integrations)
CREATE TABLE api_tokens (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    created_at   VARCHAR(30)  NOT NULL,
    last_used_at VARCHAR(30),
    CONSTRAINT fk_api_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_api_tokens_hash ON api_tokens(token_hash);
CREATE INDEX        idx_api_tokens_user ON api_tokens(user_id);
