-- Per-user categories (like book genres/shelves, separate from free-form tags)
CREATE TABLE book_categories (
    id          CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id     CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    category    VARCHAR(100) NOT NULL,
    UNIQUE (user_id, book_id, category)
);

CREATE INDEX idx_book_categories_book_id ON book_categories(book_id);
CREATE INDEX idx_book_categories_user_id ON book_categories(user_id);
