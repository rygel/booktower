-- Shared annotations: allow users to share highlights with others on the server
ALTER TABLE book_annotations ADD COLUMN IF NOT EXISTS shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE book_annotations ADD COLUMN IF NOT EXISTS note TEXT;

CREATE INDEX IF NOT EXISTS idx_book_annotations_shared ON book_annotations(book_id, shared);

SELECT 1;
