CREATE TABLE book_notebooks (
    id         CHAR(36)     PRIMARY KEY,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL DEFAULT '',
    created_at VARCHAR(30)  NOT NULL,
    updated_at VARCHAR(30)  NOT NULL
);
