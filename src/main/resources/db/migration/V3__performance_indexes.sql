-- V3: Performance indexes for hot-path queries

-- Composite index for book_status lookups by user + status (dashboard counts, finished-this-year)
CREATE INDEX IF NOT EXISTS idx_book_status_user_status ON book_status(user_id, status);

-- Composite index for book_tags covering tag-filtered queries (getUserTags, getBooksByTag)
CREATE INDEX IF NOT EXISTS idx_book_tags_user_tag_book ON book_tags(user_id, tag, book_id);

-- Index for book_categories user + category lookups (reading stats by category)
CREATE INDEX IF NOT EXISTS idx_book_categories_user_cat ON book_categories(user_id, category);

-- Index for reading_sessions by book (join queries in stats)
CREATE INDEX IF NOT EXISTS idx_reading_sessions_book_user ON reading_sessions(book_id, user_id);

-- Composite index for audit_log time-range queries filtered by actor
CREATE INDEX IF NOT EXISTS idx_audit_log_actor_time ON audit_log(actor_id, occurred_at);
