-- V2: multi-file audiobook chapter support

CREATE TABLE book_files (
    id           CHAR(36)     PRIMARY KEY,
    book_id      CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    track_index  INT          NOT NULL,
    title        VARCHAR(500),
    file_path    VARCHAR(500) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    duration_sec INT,
    added_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (book_id, track_index)
);

CREATE INDEX idx_book_files_book_id ON book_files(book_id);
