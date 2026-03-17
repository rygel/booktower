ALTER TABLE books ADD COLUMN subtitle VARCHAR(500);
ALTER TABLE books ADD COLUMN language VARCHAR(10);
ALTER TABLE books ADD COLUMN content_rating VARCHAR(20);
ALTER TABLE books ADD COLUMN age_rating VARCHAR(20);

CREATE TABLE book_moods (
    id       CHAR(36)    PRIMARY KEY,
    user_id  CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id  CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    mood     VARCHAR(50) NOT NULL,
    UNIQUE (user_id, book_id, mood)
);

CREATE INDEX idx_book_moods_book_id ON book_moods(book_id);
