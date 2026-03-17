ALTER TABLE books ADD COLUMN community_rating REAL;
ALTER TABLE books ADD COLUMN community_rating_count INT;
ALTER TABLE books ADD COLUMN community_rating_source VARCHAR(50);
ALTER TABLE books ADD COLUMN community_rating_fetched_at VARCHAR(30);
