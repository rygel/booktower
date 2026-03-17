CREATE TABLE book_reviews (
    id         CHAR(36)     PRIMARY KEY,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating     INT,
    title      VARCHAR(200),
    body       TEXT         NOT NULL,
    spoiler    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at VARCHAR(30)  NOT NULL,
    updated_at VARCHAR(30)  NOT NULL,
    UNIQUE (book_id, user_id)
);
