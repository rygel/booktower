CREATE TABLE notifications (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
