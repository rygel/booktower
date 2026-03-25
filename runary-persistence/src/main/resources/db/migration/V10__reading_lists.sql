-- Reading lists: ordered, trackable lists of books
CREATE TABLE IF NOT EXISTS reading_lists (
    id          CHAR(36) PRIMARY KEY,
    user_id     CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  VARCHAR(30) NOT NULL,
    updated_at  VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reading_lists_user ON reading_lists(user_id);

CREATE TABLE IF NOT EXISTS reading_list_items (
    id              CHAR(36) PRIMARY KEY,
    list_id         CHAR(36) NOT NULL REFERENCES reading_lists(id) ON DELETE CASCADE,
    book_id         CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    sort_order      INT NOT NULL DEFAULT 0,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at    VARCHAR(30),
    added_at        VARCHAR(30) NOT NULL,
    UNIQUE (list_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_reading_list_items_list ON reading_list_items(list_id);

SELECT 1;
