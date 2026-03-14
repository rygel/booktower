# BookTower Backend Architecture

## Overview

BookTower is a self-hosted digital library management application built with **Spring Boot 4.0.3** and **Java 25**. The backend follows a layered architecture pattern with clear separation of concerns.

## Technology Stack

### Core Framework
- **Spring Boot 4.0.3** - Application framework
- **Java 25** - Programming language
- **Gradle 9.3.1** - Build tool

### Data Layer
- **Spring Data JPA** - Data access abstraction
- **Hibernate 7.2.6** - ORM framework
- **MariaDB** - Primary database
- **Flyway** - Database migrations
- **HikariCP** - Connection pooling

### Security
- **Spring Security** - Authentication and authorization
- **JWT (JJWT 0.13.0)** - Token-based authentication
- **OAuth2 Client** - OIDC support

### Communication
- **REST API** - HTTP endpoints
- **WebSocket/STOMP** - Real-time communication
- **Jackson 3** - JSON serialization

### File Processing
- **PDFBox 3.0.6** - PDF processing
- **EPUB4J 4.2.3** - EPUB handling
- **JAudioTagger** - Audio metadata
- **JunRAR 7.5.8** - RAR archive support
- **Apache Commons Compress** - Archive handling

### Additional Libraries
- **MapStruct 1.6.3** - DTO mapping
- **Lombok** - Boilerplate reduction
- **Caffeine** - Caching
- **FreeMarker** - Templating
- **JSoup** - HTML parsing

## Directory Structure

```
booktower-api/src/main/java/org/booktower/
├── BookTowerApplication.java    # Application entry point
├── app/                        # Application-specific configuration
├── config/                     # Spring configuration classes
│   ├── security/              # Security configuration
│   └── ...                    # Other configs (WebSocket, etc.)
├── context/                    # Thread-local context management
├── controller/                 # REST API controllers (57 files)
├── convertor/                  # JPA attribute converters
├── crons/                      # Scheduled tasks
├── exception/                  # Global exception handling
├── interceptor/                # Request interceptors
├── mapper/                     # MapStruct mappers (DTOs ↔ Entities)
├── model/                      # Data models
│   ├── dto/                   # Data Transfer Objects
│   ├── entity/                # JPA entities (40+ entities)
│   ├── enums/                 # Enumerations
│   └── websocket/             # WebSocket message models
├── repository/                 # Spring Data repositories (30+ repos)
├── service/                    # Business logic services (43 files)
├── task/                       # Background task management
└── util/                       # Utility classes
```

## Architectural Patterns

### 1. Layered Architecture

The application follows a strict layered approach:

```
┌─────────────────────────────────────┐
│         Controller Layer            │  ← REST endpoints, input validation
│    (57 controllers, REST APIs)      │
├─────────────────────────────────────┤
│          Service Layer              │  ← Business logic, transactions
│    (43 services, domain logic)      │
├─────────────────────────────────────┤
│         Repository Layer            │  ← Data access abstraction
│    (Spring Data JPA repos)          │
├─────────────────────────────────────┤
│         Database Layer              │  ← MariaDB persistence
└─────────────────────────────────────┘
```

### 2. Domain Model Pattern

- **Entities**: 40+ JPA entities with relationships (Book, Author, Library, User, etc.)
- **DTOs**: Separate data transfer objects for API responses
- **Mappers**: MapStruct for automatic entity-DTO conversion

### 3. Repository Pattern

Spring Data JPA repositories provide:
- Basic CRUD operations
- Query methods (e.g., `findByUsername`)
- Custom JPQL queries with `@Query`
- Projections for optimized queries

### 4. Dependency Injection

Constructor-based injection throughout:
```java
@Service
@AllArgsConstructor
public class BookService {
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    // ...
}
```

### 5. WebSocket Communication

STOMP-based WebSocket for real-time features:
- `/queue/*` - User-specific messages
- `/topic/*` - Broadcast messages
- Authentication via WebSocketAuthInterceptor

## Key Components

### Controllers (57 total)
Examples:
- `BookController` - Book CRUD, metadata, progress
- `LibraryController` - Library management
- `UserController` - User management
- `MetadataController` - Metadata fetching/processing

### Services (43 total)
Organized by domain:
- `book/` - Book-related operations
- `metadata/` - Metadata fetching and management
- `progress/` - Reading progress tracking
- `security/` - Authentication services
- `task/` - Background task execution

### Entities (40+)
Key entities:
- `BookEntity` - Core book information
- `BookFileEntity` - File attachments
- `BookMetadataEntity` - Book metadata
- `LibraryEntity` - Library definitions
- `UserEntity` - User accounts
- `ReadingProgressEntity` - User reading state

### Security Architecture

- **JWT-based authentication** with refresh tokens
- **Role-based access control** (RBAC)
- **Method-level security** with `@PreAuthorize`
- **OIDC/OAuth2 support** for external identity providers
- **Custom annotations** like `@CheckBookAccess`

### Background Processing

- **Task system** for long-running operations
- **Async methods** with `@Async`
- **Scheduled tasks** with `@Scheduled`
- **Virtual threads** enabled (Java 21+ feature)

### Caching

- **Caffeine cache** for frequently accessed data
- **Spring Cache abstraction** with `@Cacheable`

## API Design

### REST Endpoints
- Base path: `/api/v1/`
- Resource-oriented URLs
- HTTP methods: GET, POST, PUT, PATCH, DELETE
- Response codes: 200, 201, 400, 401, 403, 404

### WebSocket Topics
User-specific:
- `/user/queue/book-add` - New book notifications
- `/user/queue/book-update` - Book update notifications
- `/user/queue/log` - Log notifications
- `/user/queue/task-progress` - Task progress updates

## Database Schema

- **Flyway migrations** in `db/migration/`
- **Physical naming strategy** for table/column names
- **Batch operations** enabled for performance
- **UTC timezone** for all timestamps

## Configuration

Externalized configuration via `application.yaml`:
- Database connection
- Security settings
- CORS configuration
- Thread pool settings
- Feature toggles

## Notable Features

1. **Multi-format support**: PDF, EPUB, CBZ/CBR, audiobooks
2. **Metadata fetching**: Integration with external sources
3. **Reading progress**: Track reading state across devices
4. **Kobo sync**: Integration with Kobo e-readers
5. **OPDS support**: Catalog protocol for e-book readers
6. **Virtual threads**: Enhanced concurrency (Java 25)
