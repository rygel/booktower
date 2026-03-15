# Quick Start

## Prerequisites

- Java 17 or later (`java -version`)
- Maven 3.8 or later (`mvn -version`)

## Run locally

```bash
# Clone and enter the directory
git clone <repo-url>
cd booktower

# Start the server (compiles and runs)
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

Open `http://localhost:8080` in your browser. Register a new account — the first registered user becomes an admin.

### Using the dev script

```bash
bash dev.sh
```

This sets sensible local defaults and starts the server with auto-restart on file changes (if configured).

## Run tests

```bash
# Full test suite (892 tests)
mvn test

# A specific test class
mvn test -Dtest=BookServiceTest

# A specific test method
mvn test -Dtest="BookServiceTest#createBook creates book in library"
```

## Configuration

The defaults work for local development with no setup required. For production, set environment variables:

```bash
BOOKTOWER_ENV=production \
BOOKTOWER_JWT_SECRET=your-long-random-secret \
BOOKTOWER_DB_URL=jdbc:h2:file:/data/booktower \
BOOKTOWER_BOOKS_PATH=/data/books \
BOOKTOWER_COVERS_PATH=/data/covers \
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

See [CONFIGURATION.md](CONFIGURATION.md) for the full variable reference.

## First steps in the UI

1. Register at `/register`
2. Create a library — give it a name and a folder path on disk
3. Click **Add Book** to add books manually, or **Scan Folder** to import existing files
4. Open a book → **Upload File** to attach a PDF, EPUB, CBZ, or CBR
5. Click **Read** to open the in-browser reader

## Troubleshooting

**Port already in use**

```bash
# Change the port
BOOKTOWER_PORT=9090 mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

**Data directories missing**

BookTower creates `./data/books`, `./data/covers`, and `./data/temp` automatically on startup. If you see a permission error, create them manually or point the env vars at a writable location.

**Tests fail with H2 errors**

The test suite uses an in-memory H2 database with Flyway migrations applied on the first test run. Run `mvn clean test` to reset state if the schema gets out of sync.
