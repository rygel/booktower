CREATE TABLE book_formats (
    id         CHAR(36)     PRIMARY KEY,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    file_path  VARCHAR(500) NOT NULL,
    file_size  BIGINT       NOT NULL DEFAULT 0,
    format     VARCHAR(20)  NOT NULL,
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    label      VARCHAR(100),
    added_at   VARCHAR(30)  NOT NULL
);
