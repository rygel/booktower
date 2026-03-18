-- Performance: add indexes for columns frequently used in WHERE/JOIN clauses
-- that were missing from earlier migrations.

-- book_reviews: queried by (book_id, user_id) and by user_id alone
CREATE INDEX IF NOT EXISTS idx_book_reviews_book_id ON book_reviews(book_id);
CREATE INDEX IF NOT EXISTS idx_book_reviews_user_id ON book_reviews(user_id);

-- book_notebooks: queried by book_id, user_id, and (book_id, user_id)
CREATE INDEX IF NOT EXISTS idx_book_notebooks_book_id ON book_notebooks(book_id);
CREATE INDEX IF NOT EXISTS idx_book_notebooks_user_id ON book_notebooks(user_id);

-- book_formats: queried by book_id
CREATE INDEX IF NOT EXISTS idx_book_formats_book_id ON book_formats(book_id);

-- library_paths: queried by library_id
CREATE INDEX IF NOT EXISTS idx_library_paths_library_id ON library_paths(library_id);

-- email_providers: filtered by is_default
CREATE INDEX IF NOT EXISTS idx_email_providers_default ON email_providers(is_default);

-- book_moods: queried by user_id (book_id index already exists)
CREATE INDEX IF NOT EXISTS idx_book_moods_user_id ON book_moods(user_id);
