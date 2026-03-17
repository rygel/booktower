CREATE TABLE book_audiobook_meta (
    book_id       CHAR(36)     PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    narrator      VARCHAR(255),
    abridged      BOOLEAN      NOT NULL DEFAULT FALSE,
    audio_cover   VARCHAR(500),
    duration_sec  INT,
    updated_at    VARCHAR(30)  NOT NULL
);
