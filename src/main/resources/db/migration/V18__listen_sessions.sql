-- V18: audiobook listening sessions and progress

CREATE TABLE listen_sessions (
    id              CHAR(36)     PRIMARY KEY,
    user_id         CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id         CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    start_pos_sec   INT          NOT NULL DEFAULT 0,
    end_pos_sec     INT          NOT NULL DEFAULT 0,
    seconds_listened INT         NOT NULL DEFAULT 0,
    session_at      VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_listen_sessions_user_id   ON listen_sessions(user_id);
CREATE INDEX idx_listen_sessions_book_id   ON listen_sessions(book_id);
CREATE INDEX idx_listen_sessions_user_date ON listen_sessions(user_id, session_at);

-- Track current playback position per user/book
CREATE TABLE listen_progress (
    user_id        CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id        CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    position_sec   INT          NOT NULL DEFAULT 0,
    total_sec      INT,
    updated_at     VARCHAR(30)  NOT NULL,
    PRIMARY KEY (user_id, book_id)
);
