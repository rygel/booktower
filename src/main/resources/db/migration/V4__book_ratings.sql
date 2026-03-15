CREATE TABLE IF NOT EXISTS book_ratings (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);
CREATE INDEX IF NOT EXISTS idx_book_ratings_user ON book_ratings(user_id);
