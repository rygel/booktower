# BookTower TODO

## Rules

- **Tests MUST use Flyway migrations** — never create tables manually with raw SQL. Use the existing migrations via `Database.connect()` or `TestFixture.database`. Manual table creation is an anti-pattern that breaks when schema changes.

## Remaining

### Features
- [ ] Reading lists — curated ordered lists of books with completion tracking (like playlists)
- [ ] Shared annotations — share highlights/notes with other users on the same server
- [ ] Want-to-read from external sources — browse and add books to a wishlist without owning the file
- [ ] Duplicate page detection UI — surface ComicPageHashService results for user cleanup
- [ ] CBL reading list import — import ComicRack reading lists (.cbl files)
- [ ] Server-wide announcements — admin can post messages visible to all users
- [ ] Reading streaks widget — daily streak counter on dashboard (data exists in ReadingStatsService)
- [ ] Book condition tracker — for physical collections: condition, purchase price, shelf location
- [ ] Reading speed analytics — pages/hour from session data, estimated time to finish current book
- [ ] Health check endpoint (`/health`) — DB status, disk space, version for Docker/Kubernetes

### Performance
- [ ] Profile actual page load times end-to-end and identify remaining bottlenecks

### Testing
- [ ] Book delivery to Kindle — needs real email + Kindle device
- [ ] Integration tests for untested services (9 services)

### Future
- [ ] Standalone KOReader/Kobo device simulator (sub-Maven module for user self-testing)
- [ ] ISBN barcode scanner (browser camera API)
- [ ] Text-to-speech in reader (browser Web Speech API)
- [ ] Import from Calibre (metadata.db)
- [ ] Email digest (weekly reading stats summary)

## Completed

- [x] KOReader + Kobo sync integration tests (17 tests, PR #107)
- [x] Configurable page gaps for comic reader (PR #106)
- [x] Per-book language FTS config — 30 languages (PR #105)
- [x] HTTP Range-based resumable downloads (PR #104)
- [x] Replace raw SQL in tests with service method calls (PR #102)
- [x] Koin DI resolution test — 41 bindings verified (PR #101)
- [x] Comic reader: double page, continuous scroll, fit modes, transitions, vertical, page gaps
- [x] Mobile responsive layout — handled by outerstellar-platform (hamburger menu, breakpoints, touch targets)
- [x] Dark mode preview in theme selector — handled by outerstellar-platform (luminance detection, color swatches)
- [x] Connection pool tuning (maxLifetime, leak detection, JDBC4 validation)
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
