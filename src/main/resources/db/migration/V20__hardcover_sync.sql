-- V20: Hardcover.app sync — book ID mappings per user

CREATE TABLE hardcover_book_mappings (
    user_id              CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id              CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    hardcover_book_id    INT          NOT NULL,
    hardcover_edition_id INT,
    last_synced_at       VARCHAR(30),
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX idx_hardcover_mappings_user ON hardcover_book_mappings(user_id);
