CREATE TABLE book_journal_entries (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title      VARCHAR(255),
    content    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_journal_user_book ON book_journal_entries(user_id, book_id);
CREATE INDEX idx_journal_user      ON book_journal_entries(user_id);
