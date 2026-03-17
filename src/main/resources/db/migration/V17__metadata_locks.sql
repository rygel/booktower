-- Stores which metadata fields are locked for a book (won't be overwritten by auto-fetch)
CREATE TABLE book_metadata_locks (
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    field_name VARCHAR(50)  NOT NULL,
    locked_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (book_id, field_name)
);
