# BookTower TODO

## Rules

- **Tests MUST use Flyway migrations** — never create tables manually with raw SQL. Use the existing migrations via `Database.connect()` or `TestFixture.database`. Manual table creation is an anti-pattern that breaks when schema changes.

## Remaining

### Comic Reader
- [ ] Smooth page transitions with animation toggle
- [ ] Vertical reading direction (top-to-bottom)
- [ ] Configurable page gaps for continuous mode

### Full-Text Search
- [ ] Multi-language FTS config per book (use book's `language` field to select PG text search config)

### Performance
- [ ] Profile actual page load times end-to-end and identify remaining bottlenecks

### UI
- [ ] Improved mobile responsive layout
- [ ] Dark mode preview in theme selector

### Testing
- [ ] Kobo/KOReader sync — tested with mocks only, never with real hardware
- [ ] Book delivery to Kindle — needs real email + Kindle device

## Completed

- [x] Connection pool tuning (maxLifetime, leak detection, JDBC4 validation)
- [x] Comic reader: double page spread, continuous scroll, 5 fit modes, click zones, swipe, preloading
- [x] Activity log page (background tasks + audit log)
- [x] BM25 index auto-creation when pg_textsearch available
- [x] CJK trigram search (pg_trgm fallback for Chinese/Japanese/Korean)
- [x] Weekly CI integration tests (FTS on PostgreSQL, OIDC on Keycloak)
- [x] OIDC E2E test against real Keycloak 26.2 (8 tests)
- [x] All container images pinned to specific versions
- [x] FTS E2E tests: real EPUB + PDF upload → extract → index → search (14 tests)
- [x] FTS enqueue bug fix: FileHandler was not indexing uploaded EPUBs/PDFs
- [x] FtsServiceTest: replaced manual SQL with Flyway migrations
- [x] Resumable downloads with Content-Length verification
- [x] Apache Tika evaluation: not worth adding now (PDFBox sufficient)
- [x] Cover image LRU cache (already implemented — Caffeine 200 entries)
- [x] MagicShelf LIMIT (already implemented — default 200)
- [x] seedFullDemo one-click (already calls seedFiles + seedLibrivox + seedComics)
- [x] Email delivery tested with real SMTP (GreenMail)
- [x] Audit all tests for manual SQL — none found
- [x] Request body size limits (1MB JSON, 500MB uploads)
- [x] String length validation, numeric range validation
- [x] Optimistic locking for book updates
- [x] Disk space check before file uploads
- [x] Unicode/edge case tests (14 tests)
- [x] Native binary: complete GraalVM reflection config (943 classes)
- [x] SSE notifications streaming with heartbeats
- [x] Download retry button
- [x] Permission boundary tests (14 tests)
- [x] Admin sidebar visibility fix (optionalAuthFilter)
- [x] Ownership bypass fixes (setStatus, setRating, setTags)
- [x] XXE fix in LibriVox RSS parser
- [x] Full-text search: websearch_to_tsquery, weighted metadata, BM25 backend
- [x] CI security suite: CodeQL, Gitleaks, Hadolint, Checkov, OSSF Scorecard
- [x] SECURITY.md + branch protection + Dependabot grouped PRs
- [x] Docker release build speed (reuse fat JAR)
