# Contributing to BookTower

Thank you for your interest in contributing to BookTower. This guide covers everything you need to get started.

## Prerequisites

- **JDK 21** — [Eclipse Temurin](https://adoptium.net/) recommended
- **Maven 3.9+** — or use the included `mvnw` wrapper
- **Git**

Optional:

- **Docker** — for building and testing container images
- **PostgreSQL 16** — only needed if testing against PostgreSQL (H2 is used by default)

## Local Development Setup

```bash
# Clone the repository
git clone https://github.com/rygel/booktower.git
cd booktower

# Compile (includes template precompilation)
mvn compile

# Run the application in dev mode
mvn exec:java
```

BookTower starts at `http://localhost:9999` by default. The first user to register becomes an admin.

### Running Tests

BookTower uses integration tests extensively. Always scope test runs to the affected test class:

```bash
# Compile tests without running them
mvn test-compile

# Run a single test class
mvn test -Dtest="BookIntegrationTest"

# Run a single test method
mvn test -Dtest="BookIntegrationTest#testCreateBook"
```

**Do not run the full test suite** (`mvn test`) without good reason — there are 400+ tests and the suite takes significant time. Target only the classes affected by your changes.

Expected timing for a single test class: 40-90 seconds.

### Database

Development and tests use an embedded H2 database in PostgreSQL compatibility mode. No database setup is required.

All SQL must be compatible with both H2 and PostgreSQL.

## Code Style

BookTower enforces code formatting with **Spotless** (using **ktlint** rules). Formatting is checked automatically during `mvn verify`.

To auto-fix formatting:

```bash
mvn spotless:apply
```

General guidelines:

- Write idiomatic Kotlin
- Prefer `val` over `var`
- Use data classes for DTOs and value objects
- Keep functions short and focused
- Improve existing code rather than removing or replacing it
- Do not add features beyond what is requested in the issue

### Project Structure

```
src/
  main/kotlin/org/booktower/
    config/       # Application configuration, DI (Koin), JSON setup
    filters/      # HTTP filters (auth, CSRF, rate limiting, logging)
    handlers/     # HTTP request handlers
    models/       # Data classes, DTOs
    routers/      # Route definitions
    services/     # Business logic, database access
  main/jte/       # JTE templates (server-side rendered HTML)
  main/resources/ # Static assets, i18n message bundles, config files
  test/kotlin/    # Integration and unit tests
```

## Testing Requirements

- **Prefer integration tests** over unit tests. Integration tests in `src/test/kotlin/org/booktower/integration/` test the full HTTP stack.
- Every new feature or bug fix should include test coverage.
- Tests must pass individually (`mvn test -Dtest="YourTestClass"`) and as part of the full suite.
- Use the shared `IntegrationTestBase` for integration tests to avoid JTE classloader conflicts.

## Internationalization

All user-facing strings must be externalized through `I18nService`. Add keys to:

- `src/main/resources/messages.properties` (English, default)
- `src/main/resources/messages_fr.properties` (French)
- `src/main/resources/messages_de.properties` (German)

## Pull Request Process

1. **Branch from `develop`** — create a feature or fix branch:
   ```bash
   git checkout develop
   git pull
   git checkout -b feat/your-feature
   ```

2. **Make your changes** — keep commits focused and well-scoped.

3. **Run affected tests** — verify your changes do not break existing functionality:
   ```bash
   mvn test -Dtest="RelevantIntegrationTest"
   ```

4. **Format your code**:
   ```bash
   mvn spotless:apply
   ```

5. **Push and open a PR** targeting the `develop` branch.

6. **PR review** — address any feedback. The CI pipeline runs the full test suite, Spotless check, Detekt static analysis, and SpotBugs.

## Commit Message Format

Use clear, descriptive commit messages:

```
<type>: <short summary>

<optional longer description>
```

Types:

- `feat` — new feature
- `fix` — bug fix
- `refactor` — code restructuring without behavior change
- `test` — adding or updating tests
- `docs` — documentation changes
- `chore` — build, CI, dependency updates

Examples:

```
feat: add OPDS catalog support for audiobook chapters
fix: prevent duplicate library scan when auto-scan triggers during manual scan
refactor: extract device sync routes into DeviceSyncRouter
test: add integration tests for KOReader progress sync
```

## CI Pipeline

Pull requests are validated by GitHub Actions:

- **Build** — `mvn package -DskipTests` on Linux, macOS, and Windows
- **Test** — full test suite on all three platforms
- **Spotless** — code formatting check
- **Detekt** — Kotlin static analysis
- **SpotBugs** — bytecode bug detection

All checks must pass before a PR can be merged.
