-- V5: User-created book collections (manual curated lists)
CREATE TABLE IF NOT EXISTS collections (
    id          CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_collections_user ON collections(user_id);

CREATE TABLE IF NOT EXISTS collection_books (
    collection_id CHAR(36) NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    book_id       CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    sort_order    INT      NOT NULL DEFAULT 0,
    added_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, book_id)
);
CREATE INDEX IF NOT EXISTS idx_collection_books_coll ON collection_books(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_books_book ON collection_books(book_id);
