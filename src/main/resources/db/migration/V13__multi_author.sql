CREATE TABLE book_authors (
    id           CHAR(36)      PRIMARY KEY,
    book_id      CHAR(36)      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    author_name  VARCHAR(255)  NOT NULL,
    author_order INT           NOT NULL DEFAULT 0,
    UNIQUE (book_id, author_name)
);

CREATE INDEX idx_book_authors_book_id ON book_authors(book_id);
