CREATE TABLE IF NOT EXISTS book_tags (
    id      CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    tag     VARCHAR(50) NOT NULL,
    UNIQUE (user_id, book_id, tag)
);
CREATE INDEX IF NOT EXISTS idx_book_tags_user_book ON book_tags(user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_book_tags_user_tag ON book_tags(user_id, tag);
