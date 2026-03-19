-- V4: Book sharing via public share tokens
ALTER TABLE books ADD COLUMN IF NOT EXISTS share_token CHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS idx_books_share_token ON books(share_token);
