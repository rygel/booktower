# Runary — Agent Instructions

## Testing Rules (CRITICAL)

**Never run the full test suite without explicit user approval.**

- Always use `-Dtest="SpecificTestClass"` to scope test runs to affected classes only
- Compilation check: `mvn test-compile` (~50s, no tests executed)
- Single class: `mvn test -Dtest="AdminIntegrationTest"` with a **90-second Bash timeout**
- If a test run approaches the timeout, kill it and report — do not let it run unbounded
- A test run longer than 10 minutes indicates a problem — kill it and investigate
- Never run `mvn test` (all 400+ tests) unless the user explicitly asks
- **After every test run, report: class name, tests passed/failed, and elapsed time (from Maven output)**
- Expected elapsed time per single class: 40–90 seconds. Flag anything over 2 minutes immediately.

## Architecture Notes

- **Stack**: Kotlin + http4k + JTE templates + JDBI + H2 (dev/test) or PostgreSQL (production)
- **Templates**: JTE precompiled by Maven plugin; dynamic engine used in dev/test mode
  - `TemplateRenderer` must be a **singleton in tests** (`TestFixture.templateRenderer`) — multiple instances cause JTE classloader conflicts and 500 errors in the full test suite
- **Auth**: JWT in `token` cookie; admin claim propagated as `X-Auth-Is-Admin` header by `JwtAuthFilter`
- **i18n**: `I18nService` via `messages*.properties`; all new UI strings need keys in `messages.properties`, `messages_fr.properties`, `messages_de.properties`

## Database

- H2 PostgreSQL mode for dev/test: `jdbc:h2:mem:runary-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`
- PostgreSQL for production via `RUNARY_DB_*` env vars
- SQL must be compatible with both H2 and PostgreSQL

## Code Style

- Improve existing code rather than removing or replacing it
- Prefer integration tests over unit tests
- Do not add features beyond what is requested
