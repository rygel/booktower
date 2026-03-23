-- Add composite index for book_moods lookups (matches book_tags and book_categories patterns from V3)
CREATE INDEX IF NOT EXISTS idx_book_moods_user_mood ON book_moods(user_id, mood);
