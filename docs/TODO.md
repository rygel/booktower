# BookTower TODO

## Rules

- **Tests MUST use Flyway migrations** — never create tables manually with raw SQL. Use the existing migrations via `Database.connect()` or `TestFixture.database`. Manual table creation is an anti-pattern that breaks when schema changes.

## Remaining

### UI needed (API exists, no user interface)
- [ ] Search bar in top navigation + advanced search page
- [ ] Library statistics dashboard
- [ ] Webhooks management UI (create, list, delete, toggle)
- [ ] Reading timeline page
- [ ] Reading goals progress card on dashboard
- [ ] Annotation export buttons (Markdown, CSV download)
- [ ] Smart discovery / recommendations page
- [ ] Database backup/restore UI in admin panel
- [ ] Custom metadata fields UI (define fields, set values on books)
- [ ] Public profile settings toggle + public profile page
- [ ] Reading speed analytics card
- [ ] Reading streaks widget on dashboard
- [ ] Book condition tracker UI on book detail page
- [ ] Reading lists page (create, reorder, toggle completion)
- [ ] Shared annotations display in reader
- [ ] Wishlist page
- [ ] Duplicate detection + merge UI in admin panel
- [ ] Bulk metadata refresh button in admin panel
- [ ] Batch import UI in admin panel
- [ ] Collections management page
- [ ] Filter presets UI (save/load/delete)
- [ ] Book drop UI (list pending, import, discard)
- [ ] Metadata proposals review UI
- [ ] Alternative covers picker
- [ ] Library health check dashboard
- [ ] Email provider config UI
- [ ] Scheduled tasks management UI
- [ ] OPDS credentials settings
- [ ] Content restrictions settings
- [ ] Kobo/KOReader device management UI
- [ ] Hardcover.app sync settings
- [ ] Book delivery config + send button
- [ ] Position sync indicator in reader

### Features (new)
- [ ] Duplicate page detection UI — surface comic page hash results
- [ ] CBL reading list import (.cbl files)
- [ ] Server-wide announcements

### Performance
- [ ] Profile actual page load times end-to-end

### Testing
- [ ] Book delivery to Kindle — needs real device
- [ ] Integration tests for untested services

### Future
- [ ] Standalone KOReader/Kobo device simulator
- [ ] ISBN barcode scanner (browser camera API)
- [ ] Text-to-speech in reader (Web Speech API)
- [ ] Import from Calibre (metadata.db)
- [ ] Email digest (weekly reading stats)

## Completed

- [x] Reading streaks widget, book condition tracker, reading speed analytics, health check (PR #123)
- [x] Public reading activity profile (PR #122)
- [x] Custom metadata fields (PR #121)
- [x] Table of contents sidebar for EPUB/PDF reader (PR #120)
- [x] Batch import from directory (PR #119)
- [x] Slim fat JAR — 90MB → 37MB (PR #117)
- [x] Release workflow fix — publish without native builds (PR #116)
- [x] Library stats, webhooks, timeline, goals, annotation export, discovery, backup, position sync, OPDS 2.0, duplicate merge (PR #114)
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
