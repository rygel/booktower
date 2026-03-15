-- Reading session log: one row per progress update that advanced at least 1 page.
-- Records start/end page and a precise timestamp so users can review their
-- reading history per-book and across all books.
CREATE TABLE reading_sessions (
    id          CHAR(36)    PRIMARY KEY,
    user_id     CHAR(36)    NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    book_id     CHAR(36)    NOT NULL REFERENCES books(id)  ON DELETE CASCADE,
    start_page  INT         NOT NULL DEFAULT 0,
    end_page    INT         NOT NULL,
    pages_read  INT         NOT NULL DEFAULT 0,
    session_at  VARCHAR(30) NOT NULL
);

CREATE INDEX idx_reading_sessions_user_id   ON reading_sessions (user_id);
CREATE INDEX idx_reading_sessions_book_id   ON reading_sessions (book_id);
CREATE INDEX idx_reading_sessions_user_date ON reading_sessions (user_id, session_at);
