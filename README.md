# BookTower

A personal digital library management system built with Kotlin, http4k, and HTMX.

## Quick Start

```powershell
# Start the application
.\start-server.ps1

# Run tests
.\run-tests.ps1

# Manual testing
.\MANUAL_TEST.ps1
```

## Documentation

All project documentation is located in the [docs](./docs/) directory:

- [README](./docs/README.md) - Main project documentation
- [QUICKSTART](./docs/QUICKSTART.md) - Quick start guide
- [BACKEND_ARCHITECTURE](./docs/BACKEND_ARCHITECTURE.md) - Backend architecture
- [FRONTEND_ARCHITECTURE](./docs/FRONTEND_ARCHITECTURE.md) - Frontend architecture
- [HTMX_TEST_COVERAGE](./docs/HTMX_TEST_COVERAGE.md) - HTMX testing documentation

## Project Structure

```
booktower/
├── src/                    # Source code
│   ├── main/              # Application code
│   └── test/               # Test code
├── docs/                   # Documentation
├── data/                   # Data directory
├── pom.xml                # Maven configuration
├── start-server.ps1        # Start application
├── run-tests.ps1          # Run tests
└── MANUAL_TEST.ps1         # Manual testing
```

## Technology Stack

- **Backend**: Kotlin, http4k, JDBI3
- **Frontend**: HTMX, JTE templates, Tailwind CSS
- **Database**: H2 (in-memory)
- **Testing**: JUnit 5, MockK
- **Build**: Maven

## Development

```powershell
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Run application
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

## License

[Add your license information here]
