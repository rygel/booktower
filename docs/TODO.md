# BookTower TODO

## Rules

- **Tests MUST use Flyway migrations** — never create tables manually with raw SQL. Use the existing migrations via `Database.connect()` or `TestFixture.database`. Manual table creation is an anti-pattern that breaks when schema changes.

## Remaining

### High Priority — Features
- [ ] Reading challenges / goals dashboard — "25 books in 2026" with progress ring, monthly pacing (reading sessions data already exists)
- [ ] Bulk metadata refresh — background scan for books missing covers/ISBNs, auto-fetch from OpenLibrary/Google Books
- [ ] Library sharing between users — let users share individual libraries with each other (LibraryAccessService is admin-only today)
- [ ] Smart recommendations — "readers who liked X also liked Y" based on tags/categories/ratings across users (RecommendationService exists)

### Medium Priority — Features
- [ ] Reading history timeline — visual "what I read and when" timeline from existing session data
- [ ] Duplicate book merge — DuplicateDetectionService finds duplicates, add ability to merge entries (keep best metadata, delete the other)
- [ ] OPDS 2.0 — current is OPDS 1.2; v2.0 is JSON-based and supports streaming audiobooks natively
- [ ] Webhook notifications — Discord/Slack/generic webhook endpoints for events (new book added, download complete, etc.)

### Differentiation
- [ ] Multi-user book clubs — shared reading with discussion threads per chapter
- [ ] Annotation export — export highlights/annotations to Markdown, Obsidian, or Readwise format
- [ ] Library statistics page — total books, format breakdown, author distribution, genre cloud, storage usage

### Testing
- [ ] Book delivery to Kindle — needs real email + Kindle device

### Future
- [ ] Standalone KOReader/Kobo device simulator (sub-Maven module for user self-testing)

## Completed

- [x] KOReader + Kobo sync integration tests (17 tests, PR #107)
- [x] Configurable page gaps for comic reader (PR #106)
- [x] Per-book language FTS config — 30 languages (PR #105)
- [x] HTTP Range-based resumable downloads (PR #104)
- [x] Replace raw SQL in tests with service method calls (PR #102)
- [x] Koin DI resolution test — 41 bindings verified (PR #101)
- [x] Comic reader: double page, continuous scroll, fit modes, transitions, vertical, page gaps
- [x] Mobile responsive layout — handled by outerstellar-platform
- [x] Dark mode preview in theme selector — handled by outerstellar-platform
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
- [x] Cover image LRU cache (Caffeine 200 entries)
- [x] MagicShelf LIMIT (default 200)
- [x] seedFullDemo one-click
- [x] Email delivery tested with real SMTP (GreenMail)
- [x] Request body size limits (1MB JSON, 500MB uploads)
- [x] Optimistic locking for book updates
- [x] Native binary: complete GraalVM reflection config (943 classes)
- [x] SSE notifications streaming with heartbeats
- [x] Permission boundary tests (14 tests)
- [x] Admin sidebar visibility fix (optionalAuthFilter)
- [x] Ownership bypass fixes (setStatus, setRating, setTags)
- [x] XXE fix in LibriVox RSS parser
- [x] Full-text search: websearch_to_tsquery, weighted metadata, BM25 backend
- [x] CI security suite: CodeQL, Gitleaks, Hadolint, Checkov, OSSF Scorecard
- [x] SECURITY.md + branch protection + Dependabot grouped PRs
- [x] Docker release build speed (reuse fat JAR)
