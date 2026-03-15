-- Book series support
ALTER TABLE books ADD COLUMN series       VARCHAR(255);
ALTER TABLE books ADD COLUMN series_index REAL;

CREATE INDEX idx_books_series ON books(series);
