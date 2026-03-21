# BookTower TODO

## Rules

- **Tests MUST use Flyway migrations** — never create tables manually with raw SQL. Use the existing migrations via `Database.connect()` or `TestFixture.database`. Manual table creation is an anti-pattern that breaks when schema changes.

## Bugs / Gaps (fix before next release)

- [ ] Retag v0.6.6 after merging PR #96 — native builds fail because tag was created before latest fixes
- [x] Fix FtsServiceTest — now uses `FtsService.indexContent()` instead of raw SQL (PR #102)
- [x] Audit ALL tests for manual table creation — no `CREATE TABLE` in tests; raw SQL for admin/user/FTS replaced with service calls (PR #102)

## High Priority

### Comic Reader Upgrade
- [ ] Double page spread mode (side-by-side pages, with "no cover" variant)
- [ ] Continuous/webtoon scroll mode (vertical infinite scroll with lazy loading)
- [ ] Multiple fit modes: width, height, screen, original, shrink-only
- [ ] Click zone navigation (left/right quarters for prev/next, center for menu)
- [ ] Swipe gesture support (touch devices)
- [ ] Image preloading (±2 pages from current)
- [ ] Smooth page transitions with animation toggle
- [ ] Vertical reading direction (top-to-bottom)
- [ ] Proper RTL reading direction with reversed navigation
- [ ] Configurable page gaps for continuous mode

### PDF Text Extraction Upgrade
- [ ] Evaluate Apache Tika as replacement for raw PDFBox `PDFTextStripper`
- Current: `PdfTextExtractor` uses `PDFTextStripper` directly — works for standard text PDFs
- Limitations: no OCR for scanned PDFs, poor complex layout handling, RTL text issues
- Tika adds: OCR via Tesseract, dozens of formats, better layout detection

### Full-Text Search
- [x] Built-in PostgreSQL FTS with `websearch_to_tsquery` and weighted metadata
- [x] pg_textsearch BM25 optional backend (auto-detected)
- [x] FTS enqueue on file upload (was missing — fixed)
- [x] E2E tests: EPUB + PDF upload → extract → index → search (14 tests)
- [ ] CJK text search — PostgreSQL `simple` config doesn't tokenize Chinese/Japanese/Korean. Need `pg_bigm` or `pg_trgm` extension, or `zhparser`/`mecab` for specific languages.
- [ ] Multi-language FTS config per book (use book's `language` field)

## Medium Priority

### Performance
- [ ] Cover image LRU memory cache — disk I/O on every cover request. Caffeine (already a dependency), ~20 lines of code.
- [ ] MagicShelfService.getBooksForShelf() — returns unbounded results, needs LIMIT. One line fix, prevents OOM on large libraries.
- [ ] Connection pool tuning — HikariCP defaults may not be optimal
- [ ] Profile actual page load times end-to-end

### Downloads / UI
- [ ] seedFullDemo should auto-call seedFiles + seedLibrivox + seedComics — currently requires clicking 4 buttons instead of 1
- [ ] Make downloads resumable — if a download fails halfway, retry re-downloads everything from scratch. Should check if file already exists with correct size.
- [ ] Activity log page — persistent event history per user

### Testing (real services, not mocks)
- [ ] OIDC/SSO with real Keycloak — we claim SSO support but never tested with a real provider. Testcontainers has a Keycloak image.
- [ ] Email delivery with real SMTP — GreenMail tests pass but we've never sent a real email.
- [ ] Kobo/KOReader sync — tested with mocks only, never with real hardware

### Release Pipeline
- [ ] Verify Docker build speed improvement (PR #94 merged) — should be ~2 min instead of ~28 min

## Low Priority / Nice-to-Have

- [ ] CII Best Practices badge (OSSF Scorecard)
- [ ] Fuzzing setup (OSS-Fuzz)
- [ ] Signed commits and releases
- [ ] pg_textsearch BM25 index creation on `book_content.content` column
- [ ] Upgrade Kotlin to 2.3.x when CodeQL catches up

## Completed

- [x] Request body size limits (1MB JSON, 500MB uploads)
- [x] String length validation (description 10K, tags 50 chars)
- [x] Numeric range validation (pageCount >= 0, <= 100K)
- [x] Optimistic locking for book updates
- [x] Disk space check before file uploads
- [x] Unicode/edge case tests (14 tests)
- [x] Native binary: complete GraalVM reflection config (943 classes)
- [x] Native binary: JTE precompiled mode, Flyway filesystem fallback
- [x] SSE notifications streaming with heartbeats
- [x] Download retry button
- [x] Permission boundary tests (14 tests)
- [x] Admin sidebar visibility fix (optionalAuthFilter)
- [x] Ownership bypass fixes (setStatus, setRating, setTags)
- [x] XXE fix in LibriVox RSS parser
- [x] Comic downloads: archive.org access-restricted items replaced
- [x] Human-readable filenames for seeded content
- [x] Dark/light theme pair preference
- [x] Full-text search: websearch_to_tsquery, weighted metadata, BM25 backend
- [x] FTS E2E tests: real EPUB + PDF upload → extract → index → search (14 tests)
- [x] FTS enqueue bug fix: FileHandler was not enqueuing uploaded EPUBs/PDFs
- [x] CI security suite: CodeQL, Gitleaks, Hadolint, Checkov, OSSF Scorecard, zizmor, actionlint
- [x] All GitHub Actions pinned to commit SHAs
- [x] SECURITY.md + branch protection
- [x] Dependabot grouped PRs
- [x] Docker release build speed (reuse fat JAR instead of rebuilding)
