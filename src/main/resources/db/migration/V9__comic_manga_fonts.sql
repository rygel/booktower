-- Comic/manga reading direction per book (ltr = left-to-right, rtl = right-to-left)
ALTER TABLE books ADD COLUMN reading_direction VARCHAR(3) DEFAULT 'ltr';

-- Custom fonts uploaded by users for the EPUB reader
CREATE TABLE user_fonts (
    id          CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename    VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size   BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, filename)
);

CREATE INDEX idx_user_fonts_user_id ON user_fonts(user_id);
