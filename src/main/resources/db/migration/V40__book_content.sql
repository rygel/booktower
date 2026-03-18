-- Full-text search content store and indexing queue.
-- Plain SQL, compatible with H2 and PostgreSQL.
-- PostgreSQL-specific columns (tsvector, GIN index, trigger) are applied
-- programmatically by FtsService.initialize() only when FTS is enabled.
CREATE TABLE book_content (
    book_id    CHAR(36)     PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    content    TEXT,
    status     VARCHAR(10)  NOT NULL DEFAULT 'pending',
    indexed_at TIMESTAMP,
    error_msg  VARCHAR(500)
);

CREATE INDEX idx_book_content_status ON book_content(status);
