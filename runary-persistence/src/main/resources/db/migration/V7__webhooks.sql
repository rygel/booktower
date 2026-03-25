-- Webhook endpoints for event notifications
CREATE TABLE IF NOT EXISTS webhooks (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    url        VARCHAR(500) NOT NULL,
    events     VARCHAR(500) NOT NULL DEFAULT '',
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_webhooks_user ON webhooks(user_id);
