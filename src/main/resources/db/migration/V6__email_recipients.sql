-- Saved email recipients for book delivery (Kindle addresses, etc.)
CREATE TABLE email_recipients (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, email)
);
CREATE INDEX idx_email_recipients_user_id ON email_recipients(user_id);
