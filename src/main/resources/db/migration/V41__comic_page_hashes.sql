-- Queue tracking which comic books need page-hash indexing
CREATE TABLE comic_hash_queue (
    book_id    CHAR(36)     PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    status     VARCHAR(10)  NOT NULL DEFAULT 'pending',
    queued_at  TIMESTAMP    NOT NULL,
    indexed_at TIMESTAMP,
    error_msg  VARCHAR(500)
);
CREATE INDEX idx_comic_hash_queue_status ON comic_hash_queue(status);

-- Per-page average-hash values (64-bit, stored as signed BIGINT)
CREATE TABLE comic_page_hashes (
    book_id    CHAR(36)  NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page_index INT       NOT NULL,
    phash      BIGINT    NOT NULL,
    PRIMARY KEY (book_id, page_index)
);
CREATE INDEX idx_comic_page_hashes_phash ON comic_page_hashes(phash);
