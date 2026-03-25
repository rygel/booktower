-- PostgreSQL-only: adds metadata tsvector for full-text search.
-- H2 (dev/test) skips this migration via Flyway's PostgreSQL-specific
-- schema applied in FtsService.applyPgSchema() instead.
--
-- This file is intentionally empty for H2 compatibility.
-- The actual schema change is applied programmatically on PostgreSQL.
SELECT 1;
