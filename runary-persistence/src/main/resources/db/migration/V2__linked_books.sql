CREATE TABLE IF NOT EXISTS linked_books (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL,
    ebook_id   CHAR(36)    NOT NULL,
    audio_id   CHAR(36)    NOT NULL,
    created_at VARCHAR(50) NOT NULL,
    UNIQUE (user_id, ebook_id),
    UNIQUE (user_id, audio_id)
);

CREATE INDEX IF NOT EXISTS idx_linked_books_user ON linked_books(user_id);
CREATE INDEX IF NOT EXISTS idx_linked_books_ebook ON linked_books(ebook_id);
CREATE INDEX IF NOT EXISTS idx_linked_books_audio ON linked_books(audio_id);
