-- Want-to-read wishlist: books the user wants to read but doesn't own yet
CREATE TABLE IF NOT EXISTS wishlist (
    id              CHAR(36) PRIMARY KEY,
    user_id         CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255),
    isbn            VARCHAR(20),
    cover_url       VARCHAR(500),
    description     TEXT,
    source          VARCHAR(50),
    source_url      VARCHAR(500),
    notes           TEXT,
    priority        INT NOT NULL DEFAULT 0,
    added_at        VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wishlist_user ON wishlist(user_id);

SELECT 1;
