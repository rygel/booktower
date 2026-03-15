CREATE TABLE IF NOT EXISTS book_status (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_book_status_user ON book_status(user_id);
