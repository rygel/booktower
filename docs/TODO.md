# BookTower TODO

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
- [ ] CJK text search — PostgreSQL `simple` config doesn't tokenize Chinese/Japanese/Korean
- [ ] Multi-language FTS config per book (use book's `language` field)

### Code Quality
- [ ] Fix FtsServiceTest — creates tables manually with raw SQL instead of using Flyway migrations. Anti-pattern.
- [ ] Audit all other tests for manual table creation — should use Flyway or shared test fixtures

## Medium Priority

### Performance
- [ ] Cover image LRU memory cache — disk I/O on every cover request (Caffeine, ~20 lines)
- [ ] MagicShelfService.getBooksForShelf() — returns unbounded results, needs LIMIT (1 line fix)
- [ ] Connection pool tuning — HikariCP defaults may not be optimal
- [ ] Profile actual page load times end-to-end

### Downloads / UI
- [ ] Make downloads resumable — check if file already exists with correct size
- [ ] seedFullDemo should auto-call seedFiles + seedLibrivox + seedComics (one click)
- [ ] Activity log page — persistent event history per user

### Testing
- [ ] OIDC/SSO with real Keycloak — Testcontainers has a Keycloak image
- [ ] Kobo/KOReader sync — tested with mocks only, never with real hardware
- [ ] Email/Kindle delivery — tested with GreenMail, never with real SMTP

### Release Pipeline
- [ ] Retag v0.6.6 after merging PR #96 (FTS bug fix + tests)
- [ ] Docker build speed fix already merged (PR #94) — verify next release builds in ~2 min

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
- [x] CI security suite: CodeQL, Gitleaks, Hadolint, Checkov, OSSF Scorecard, zizmor, actionlint
- [x] All GitHub Actions pinned to commit SHAs
- [x] SECURITY.md + branch protection
- [x] Dependabot grouped PRs
- [x] Docker release build speed (reuse fat JAR instead of rebuilding)
