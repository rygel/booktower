CREATE TABLE refresh_tokens (
    token         CHAR(36)     PRIMARY KEY,
    user_id       CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at  TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
