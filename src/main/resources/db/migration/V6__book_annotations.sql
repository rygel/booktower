CREATE TABLE IF NOT EXISTS book_annotations (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page INT NOT NULL,
    selected_text TEXT NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT 'yellow',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_book_annotations_user_book ON book_annotations(user_id, book_id);
