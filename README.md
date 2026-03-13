# BookTower

A modern book management application built with Kotlin, http4k, and HTMX. BookTower allows you to organize your digital book collection, track reading progress, and manage your libraries.

## Features

- **Library Management**: Create and manage multiple book libraries
- **Book Organization**: Import and organize books with metadata
- **Reading Progress**: Track your reading progress across all books
- **Modern UI**: Server-side rendered HTMX interface with Tailwind CSS
- **Internationalization**: Multi-language support via Weblate integration
- **Multiple Themes**: Choose from various UI themes (Dark, Light, Nord, Dracula, etc.)

## Technology Stack

- **Language**: Kotlin 2.3.10
- **Framework**: http4k (functional HTTP toolkit)
- **Template Engine**: JTE (Java Template Engine)
- **Database**: H2 (with JDBI)
- **Migrations**: Flyway
- **Styling**: Tailwind CSS with HTMX
- **Build Tool**: Maven

## Prerequisites

- Java 21 or higher
- Maven 3.9+ (or use Maven wrapper after setup)

## Quick Start

### Clone and Build

```bash
git clone https://github.com/rygel/booktower.git
cd booktower
mvn clean compile
```

### Run the Application

```bash
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

The application will start on `http://localhost:9999`

### Run Tests

```bash
# Run unit tests
mvn test

# Run E2E tests
./e2e-test.sh
```

## Code Quality

This project maintains high code quality standards with multiple static analysis tools:

### Enabled Plugins

All plugins run automatically during `mvn verify`:

| Plugin | Purpose | Version |
|--------|---------|---------|
| **Checkstyle** | Java code style checking | 3.6.0 |
| **PMD** | Static analysis for bugs | 3.28.0 |
| **SpotBugs** | Bytecode analysis | 4.9.3.0 |
| **Detekt** | Kotlin static analysis | 1.23.8 |
| **Ktlint** | Kotlin linting | 3.5.0 |
| **Spotless** | Code formatting | 2.43.0 |
| **JaCoCo** | Test coverage | 0.8.14 |
| **Duplicate Finder** | Duplicate dependency check | 2.0.1 |
| **Forbidden APIs** | Forbidden API usage check | 3.9 |

### Running Code Quality Checks

```bash
# Run all checks (includes code quality)
mvn verify -Dflyway.skip=true -Dsonar.skip=true

# Run individual checks
mvn checkstyle:check
mvn pmd:check
mvn spotbugs:check
mvn detekt:check
mvn ktlint:check
mvn spotless:check

# Fix formatting issues automatically
mvn spotless:apply
```

### Code Quality Reports

Reports are generated in `target/` directory:
- Checkstyle: `target/checkstyle-result.xml`
- PMD: `target/pmd.xml`
- SpotBugs: `target/spotbugsXml.xml`
- Detekt: `target/detekt-reports/`
- JaCoCo: `target/site/jacoco/`

## Project Structure

```
booktower/
├── src/
│   ├── main/
│   │   ├── kotlin/org/booktower/
│   │   │   ├── BookTowerApp.kt          # Application entry point
│   │   │   ├── config/                  # Configuration classes
│   │   │   ├── handlers/                # HTTP request handlers
│   │   │   ├── model/                   # Domain models
│   │   │   ├── models/                  # DTOs
│   │   │   ├── services/                # Business logic
│   │   │   ├── web/                     # Web context
│   │   │   └── weblate/                 # Weblate integration
│   │   ├── jte/                         # JTE templates
│   │   └── resources/
│   │       ├── db/migration/            # Flyway migrations
│   │       └── static/                  # Static assets
│   └── test/
│       └── kotlin/org/booktower/
│           └── e2e/                     # End-to-end tests
├── pom.xml                              # Maven configuration
├── detekt.yml                           # Detekt configuration
├── checkstyle.xml                       # Checkstyle configuration
├── spotbugs-exclude.xml                 # SpotBugs exclusions
└── .editorconfig                        # Editor configuration
```

## Development

### Adding New Dependencies

Update `pom.xml` and ensure:
1. Use latest stable versions
2. Add to `dependencyManagement` section if used in multiple modules
3. Run `mvn verify` to check for duplicate dependencies

### Code Style

This project uses:
- **Spotless** with **ktlint** for automatic formatting
- **Checkstyle** for Java code style
- **Detekt** for Kotlin-specific issues

Format code before committing:
```bash
mvn spotless:apply
```

### Database Migrations

Migrations are handled by Flyway and stored in `src/main/resources/db/migration/`.

To create a new migration:
1. Create file: `V{version}__{description}.sql`
2. Run: `mvn flyway:migrate`

## Architecture

### Backend
- **http4k**: Functional HTTP toolkit for Kotlin
- **JDBI**: Fluent SQL library for database access
- **H2**: Embedded database for development

### Frontend
- **HTMX**: Server-side rendered UI with AJAX capabilities
- **Tailwind CSS**: Utility-first CSS framework
- **JTE**: Type-safe HTML templates

### Authentication
- JWT-based authentication
- BCrypt for password hashing

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Run code quality checks: `mvn verify -Dflyway.skip=true`
5. Commit your changes: `git commit -am 'Add new feature'`
6. Push to the branch: `git push origin feature/my-feature`
7. Submit a pull request

## License

[Add your license information here]

## Acknowledgments

- http4k team for the excellent functional HTTP toolkit
- HTMX for making server-side rendering interactive
- The Kotlin community for the amazing language and ecosystem
