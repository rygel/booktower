CREATE TABLE IF NOT EXISTS reading_daily (
    id       CHAR(36) PRIMARY KEY,
    user_id  CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id  CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date     VARCHAR(10) NOT NULL,
    pages_read INT NOT NULL DEFAULT 0,
    UNIQUE (user_id, book_id, date)
);

CREATE INDEX IF NOT EXISTS idx_reading_daily_user_date ON reading_daily(user_id, date);
