# Migration Plan: Angular to http4k + HTMX

## Executive Summary

This document outlines a comprehensive migration strategy to move from the current Angular 21 SPA frontend to a server-side rendered (SSR) architecture using **http4k** (Kotlin HTTP toolkit) and **HTMX**. This shift represents a move from a JavaScript-heavy SPA to a hypermedia-driven architecture following HATEOAS principles.

## Why This Migration?

### Current Angular Architecture Challenges
- **Complexity**: 404 TypeScript files, 78 services, heavy state management
- **Build size**: 6MB+ bundle despite lazy loading
- **WebSocket complexity**: STOMP over WebSocket for real-time updates
- **Development overhead**: Complex tooling, long build times
- **SEO limitations**: Client-side rendering challenges

### Benefits of http4k + HTMX
- **Simplicity**: HTML over the wire, progressive enhancement
- **Performance**: Smaller payloads, server-rendered HTML
- **SEO-friendly**: Full server-side rendering
- **Reduced complexity**: No complex state management
- **Better accessibility**: Standard HTML forms and links
- **Offline support**: Simpler to implement with Service Workers

## Current State Analysis

### Frontend Statistics
- **TypeScript files**: 404
- **Services**: 78 (state management, API calls)
- **Components**: ~100+
- **Features**: 13 (book browser, readers, metadata, etc.)
- **WebSocket topics**: 10+ user queues
- **Bundle size**: ~6MB

### API Integration Points
- **REST endpoints**: 57 controllers on backend
- **WebSocket/STOMP**: Real-time notifications, progress updates
- **File streaming**: PDF, EPUB, CBX content delivery
- **Authentication**: JWT tokens, OAuth2/OIDC

### Key Features to Migrate
1. **Book Browser**: Grid/list views, filtering, sorting, pagination
2. **Book Readers**: PDF, EPUB, CBX, Audiobook players
3. **Metadata Management**: Forms, validation, batch operations
4. **User Management**: Authentication, settings, permissions
5. **Library Management**: CRUD operations, scanning
6. **Real-time Updates**: Progress notifications, library health
7. **Search & Discovery**: Full-text search, recommendations
8. **Statistics**: Charts, reading analytics

## Target Architecture

### Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    Browser                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │   HTMX      │  │  Hyperscript │  │   Vanilla JS        │ │
│  │  (1.9.x)    │  │   (0.9.x)    │  │   (Minimal)         │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP/HTML
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   http4k Server (Kotlin)                    │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │   Routes    │  │   Handlers   │  │   View Models       │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  Templates  │  │    Forms     │  │   Validation        │ │
│  │ (Pebble/    │  │   (http4k)   │  │   (Konform)         │ │
│  │  Thymeleaf) │  │              │  │                     │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP/REST
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Existing Spring Boot Backend                   │
│         (Java - remains unchanged)                          │
└─────────────────────────────────────────────────────────────┘
```

### Core Technologies

**Backend (New)**
- **http4k**: 5.x (Kotlin HTTP toolkit)
- **Kotlin**: 2.0+
- **Ktor Client**: HTTP client to Spring Boot backend
- **Jackson**: JSON serialization
- **Pebble**: Template engine (or Thymeleaf)
- **Konform**: Validation library
- **Kotlinx.html**: Type-safe HTML generation (optional)

**Frontend (Browser)**
- **HTMX**: 1.9.x (hypermedia interactions)
- **_hyperscript**: 0.9.x (client-side scripting)
- **Tailwind CSS**: Utility-first CSS (or keep existing PrimeNG styles)
- **Chart.js**: For statistics (kept, but server-rendered)
- **Foliate.js**: EPUB reader (embedded as-is)
- **PDF.js**: For PDF viewing (or keep ngx-extended-pdf-viewer approach)

### Architecture Principles

1. **Hypermedia-Driven**: All interactions via HTML, not JSON APIs
2. **Progressive Enhancement**: Works without JavaScript, enhanced with HTMX
3. **Server-Side Rendering**: Full HTML pages from server
4. **HATEOAS**: Links drive application state
5. **Minimal JavaScript**: Only for complex interactions (readers, charts)

## Project Structure

```
booklore-web/
├── build.gradle.kts              # Gradle build configuration
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── org/booklore/web/
│   │   │       ├── BookloreWebApp.kt           # Application entry point
│   │   │       ├── config/
│   │   │       │   ├── AppConfig.kt            # Application configuration
│   │   │       │   ├── SecurityConfig.kt       # JWT/OAuth2 configuration
│   │   │       │   └── Routes.kt               # Route definitions
│   │   │       ├── handlers/
│   │   │       │   ├── BookHandlers.kt         # Book-related handlers
│   │   │       │   ├── LibraryHandlers.kt      # Library handlers
│   │   │       │   ├── AuthHandlers.kt         # Authentication handlers
│   │   │       │   ├── ReaderHandlers.kt       # Book reader handlers
│   │   │       │   ├── MetadataHandlers.kt     # Metadata management
│   │   │       │   └── ApiClient.kt            # Spring Boot API client
│   │   │       ├── models/
│   │   │       │   ├── Book.kt                 # Domain models
│   │   │       │   ├── Library.kt
│   │   │       │   ├── User.kt
│   │   │       │   └── forms/
│   │   │       │       ├── CreateBookForm.kt
│   │   │       │       └── UpdateMetadataForm.kt
│   │   │       ├── services/
│   │   │       │   ├── BookService.kt          # Business logic
│   │   │       │   ├── AuthService.kt          # Authentication service
│   │   │       │   └── NotificationService.kt  # Real-time notifications
│   │   │       └── utils/
│   │   │           ├── HtmlUtils.kt            # HTML helpers
│   │   │           └── ValidationUtils.kt      # Validation helpers
│   │   └── resources/
│   │       ├── templates/                      # Pebble/Thymeleaf templates
│   │       │   ├── layouts/
│   │       │   │   ├── base.html               # Base layout
│   │       │   │   └── main.html               # Main app layout
│   │       │   ├── books/
│   │       │   │   ├── list.html               # Book list view
│   │       │   │   ├── detail.html             # Book detail view
│   │       │   │   ├── grid.html               # Book grid partial
│   │       │   │   └── edit-form.html          # Book edit form
│   │       │   ├── readers/
│   │       │   │   ├── pdf-reader.html
│   │       │   │   ├── epub-reader.html
│   │       │   │   └── cbx-reader.html
│   │       │   ├── components/
│   │       │   │   ├── book-card.html          # Book card component
│   │       │   │   ├── pagination.html         # Pagination component
│   │       │   │   ├── filters.html            # Filter sidebar
│   │       │   │   └── notifications.html      # Notification toast
│   │       │   └── auth/
│   │       │       ├── login.html
│   │       │       └── setup.html
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   └── app.css                 # Application styles
│   │       │   ├── js/
│   │       │   │   ├── htmx.min.js             # HTMX library
│   │       │   │   ├── hyperscript.min.js      # _hyperscript
│   │       │   │   └── readers/
│   │       │   │       ├── pdf-viewer.js
│   │       │   │       ├── epub-viewer.js      # Foliate integration
│   │       │   │       └── cbx-viewer.js
│   │       │   └── assets/
│   │       │       ├── foliate/                # Foliate.js files
│   │       │       └── images/
│   │       └── application.conf                # Configuration
│   └── test/
│       └── kotlin/
│           └── org/booklore/web/
│               └── handlers/
│                   └── BookHandlerTest.kt
├── docker/
│   └── Dockerfile
└── docker-compose.yml
```

## Migration Phases

### Phase 0: Preparation & Proof of Concept (Weeks 1-2)

**Goals**:
- Set up project structure
- Build proof of concept
- Establish patterns

**Tasks**:
1. Create new Kotlin project with http4k
2. Implement basic routing
3. Create HTML templates (layout, components)
4. Integrate with existing Spring Boot API
5. Build simple book list page
6. Test HTMX interactions

**Deliverables**:
- Working POC with book list
- Template structure
- API client pattern
- Documentation

### Phase 1: Core Foundation (Weeks 3-6)

**Goals**:
- Authentication & session management
- Basic CRUD operations
- Layout and navigation

**Features**:
1. **Authentication System**
   - Login/logout pages
   - JWT token management (http-only cookies)
   - Session handling
   - OIDC/OAuth2 integration

2. **Navigation & Layout**
   - Sidebar navigation
   - Header with search
   - Responsive design
   - Breadcrumb navigation

3. **Book Browser (Basic)**
   - List view (server-rendered)
   - Basic pagination
   - Simple filtering
   - Book detail page

4. **Library Management**
   - Library list
   - Create/edit libraries
   - Library settings

**Migration Strategy**:
- Keep Angular app running
- Add reverse proxy (nginx) to route traffic
- Gradually replace Angular routes

**Deliverables**:
- Authentication working
- Book browser functional
- Library management

### Phase 2: Enhanced Book Management (Weeks 7-10)

**Goals**:
- Advanced book features
- Metadata management
- Search functionality

**Features**:
1. **Advanced Book Browser**
   - Grid view with HTMX
   - Infinite scroll (HTMX)
   - Advanced filtering (authors, series, tags)
   - Sorting options
   - Bulk operations

2. **Metadata Management**
   - Edit book metadata forms
   - Batch metadata operations
   - Metadata fetching UI
   - Cover image management

3. **Search & Discovery**
   - Full-text search
   - Search suggestions
   - Filtered search results
   - Search history

4. **Author & Series Browsers**
   - Author list and detail
   - Series navigation
   - Related books

**HTMX Patterns**:
- `hx-get` for pagination
- `hx-target` for partial updates
- `hx-trigger` for infinite scroll
- `hx-swap` for smooth transitions

**Deliverables**:
- Complete book management
- Metadata workflows
- Search functionality

### Phase 3: Readers & Complex Features (Weeks 11-14)

**Goals**:
- Book readers (most complex part)
- Real-time features
- User settings

**Features**:
1. **PDF Reader**
   - Server-rendered reader shell
   - PDF.js integration with HTMX
   - Annotation support
   - Progress tracking

2. **EPUB Reader**
   - Foliate.js integration
   - Custom controls
   - Reading progress
   - Font/theme settings

3. **CBX Reader**
   - Comic book viewer
   - Page navigation
   - Zoom controls

4. **Audiobook Player**
   - Audio player controls
   - Progress tracking
   - Playback speed

5. **Real-time Updates**
   - Server-Sent Events (SSE) instead of WebSocket
   - Library scan progress
   - Metadata fetch progress
   - Notifications

6. **User Settings**
   - Profile management
   - Preferences
   - Security settings

**Technical Approach**:
- Readers are hybrid: server shell + client JS
- Use HTMX for reader navigation
- SSE for progress (simpler than WebSocket)

**Deliverables**:
- All readers functional
- Real-time progress updates
- User settings complete

### Phase 4: Advanced Features (Weeks 15-18)

**Goals**:
- Statistics & analytics
- Magic shelves
- Bookdrop
- Notebook

**Features**:
1. **Statistics Dashboard**
   - Server-rendered charts (Chart.js)
   - Reading analytics
   - Library stats
   - User stats

2. **Magic Shelves**
   - Dynamic shelf rules
   - Smart collections
   - Shelf management

3. **Bookdrop**
   - File upload interface
   - Upload progress (SSE)
   - File review workflow

4. **Notebook**
   - Notes list
   - Note editor
   - Book annotations

5. **Admin Features**
   - User management
   - System settings
   - Audit logs
   - Task management

**Deliverables**:
- All features migrated
- Admin panel complete

### Phase 5: Optimization & Cleanup (Weeks 19-20)

**Goals**:
- Performance optimization
- Remove Angular
- Documentation

**Tasks**:
1. **Performance**
   - Template caching
   - CSS/JS minification
   - Image optimization
   - CDN setup (optional)

2. **Cleanup**
   - Remove Angular frontend
   - Update build pipelines
   - Docker updates
   - Environment configs

3. **Documentation**
   - API documentation (internal)
   - Deployment guide
   - Development guide
   - Architecture decision records

4. **Testing**
   - Unit tests (Kotest)
   - Integration tests
   - E2E tests (Playwright)
   - Performance testing

**Deliverables**:
- Production-ready application
- Complete documentation
- All tests passing

## Technical Implementation Details

### 1. HTTP4K Application Structure

```kotlin
// BookloreWebApp.kt
fun main() {
    val config = AppConfig.load()
    val apiClient = ApiClient(config.apiBaseUrl)
    val authService = AuthService(apiClient)
    val bookService = BookService(apiClient)
    
    val app = routes(
        authRoutes(authService),
        bookRoutes(bookService, authService),
        libraryRoutes(apiClient, authService),
        readerRoutes(apiClient),
        staticRoutes()
    ).withFilter(
        authenticationFilter(authService)
    ).withFilter(
        loggingFilter()
    )
    
    app.asServer(SunHttp(config.port)).start()
}
```

### 2. Route Definitions

```kotlin
// Routes.kt
fun bookRoutes(
    bookService: BookService,
    authService: AuthService
): RoutingHttpHandler = routes(
    "/books" bind GET to { req: Request ->
        val filters = extractFilters(req)
        val page = req.query("page")?.toInt() ?: 1
        val books = bookService.getBooks(filters, page)
        
        if (req.header("HX-Request") != null) {
            // HTMX request - return partial
            Response(OK).body(renderBookGrid(books))
        } else {
            // Full page request
            Response(OK).body(renderFullPage("books/list", mapOf("books" to books)))
        }
    },
    
    "/books/{id}" bind GET to { req: Request ->
        val bookId = req.path("id")!!.toLong()
        val book = bookService.getBook(bookId)
        Response(OK).body(renderBookDetail(book))
    },
    
    "/books/{id}/edit" bind GET to { req: Request ->
        val bookId = req.path("id")!!.toLong()
        val book = bookService.getBook(bookId)
        Response(OK).body(renderEditForm(book))
    },
    
    "/books/{id}/edit" bind POST to { req: Request ->
        val bookId = req.path("id")!!.toLong()
        val form = req.formAsMap()
        val result = bookService.updateBook(bookId, form)
        
        if (result.isSuccess) {
            Response(SEE_OTHER)
                .header("Location", "/books/$bookId")
                .header("HX-Trigger", "{\"showToast\": \"Book updated successfully\"}")
        } else {
            Response(BAD_REQUEST)
                .body(renderEditForm(result.book, result.errors))
        }
    }
)
```

### 3. Template Example (Pebble)

```html
<!-- templates/books/list.html -->
{% extends "layouts/main.html" %}

{% block content %}
<div class="book-browser">
    <h1>Books</h1>
    
    <!-- Filters -->
    <div class="filters" hx-get="/books" hx-target="#book-grid" hx-trigger="change">
        <select name="library" hx-get="/books" hx-target="#book-grid">
            <option value="">All Libraries</option>
            {% for library in libraries %}
            <option value="{{ library.id }}">{{ library.name }}</option>
            {% endfor %}
        </select>
        
        <input type="search" 
               name="search" 
               placeholder="Search books..."
               hx-get="/books"
               hx-target="#book-grid"
               hx-trigger="keyup changed delay:300ms, search">
    </div>
    
    <!-- Book Grid -->
    <div id="book-grid" class="book-grid">
        {% include "components/book-grid.html" %}
    </div>
    
    <!-- Pagination -->
    {% if hasMorePages %}
    <button hx-get="/books?page={{ nextPage }}"
            hx-target="#book-grid"
            hx-swap="beforeend"
            hx-trigger="revealed"
            class="load-more">
        Load More
    </button>
    {% endif %}
</div>
{% endblock %}
```

```html
<!-- templates/components/book-grid.html -->
{% for book in books %}
<article class="book-card" 
         hx-get="/books/{{ book.id }}"
         hx-target="#main-content"
         hx-push-url="true">
    <img src="{{ book.coverUrl }}" alt="{{ book.title }}" loading="lazy">
    <h3>{{ book.title }}</h3>
    <p class="author">{{ book.author }}</p>
    <span class="rating">{{ book.rating }}/5</span>
</article>
{% endfor %}
```

### 4. API Client

```kotlin
// ApiClient.kt
class ApiClient(private val baseUrl: String) {
    private val client = JavaHttpClient()
    private val jackson = ObjectMapper().registerKotlinModule()
    
    fun getBooks(filters: BookFilters, page: Int): BookListResponse {
        val request = Request(GET, "$baseUrl/api/v1/books")
            .query("page", page.toString())
            .query("libraryId", filters.libraryId?.toString())
            .query("search", filters.search)
        
        val response = client(request)
        return jackson.readValue(response.bodyString())
    }
    
    fun getBook(id: Long): Book {
        val request = Request(GET, "$baseUrl/api/v1/books/$id")
        val response = client(request)
        return jackson.readValue(response.bodyString())
    }
    
    fun updateBook(id: Long, data: Map<String, String>): Result<Book> {
        val request = Request(PATCH, "$baseUrl/api/v1/books/$id")
            .body(jackson.writeValueAsString(data))
        
        val response = client(request)
        return if (response.status.successful) {
            Result.success(jackson.readValue(response.bodyString()))
        } else {
            Result.failure(ApiException(response.status, response.bodyString()))
        }
    }
}
```

### 5. Real-time Updates with SSE

```kotlin
// NotificationService.kt
class NotificationService(private val apiClient: ApiClient) {
    
    fun streamProgress(userId: String): Stream<Response> {
        return Stream.generate {
            val progress = apiClient.getProgressUpdates(userId)
            Response(OK)
                .header("Content-Type", "text/event-stream")
                .body("data: ${jackson.writeValueAsString(progress)}\n\n")
        }
    }
}

// Route
"/api/progress" bind GET to { req: Request ->
    val userId = req.authenticatedUser().id
    Response(OK)
        .header("Content-Type", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .body(notificationService.streamProgress(userId))
}
```

```html
<!-- Include in base layout -->
<div hx-ext="sse" sse-connect="/api/progress" sse-swap="progress-update">
    <!-- Progress updates injected here -->
</div>
```

### 6. Form Handling with Validation

```kotlin
// forms/CreateBookForm.kt
data class CreateBookForm(
    val title: String,
    val author: String,
    val isbn: String?,
    val libraryId: Long
) {
    companion object {
        fun fromRequest(req: Request): ValidationResult<CreateBookForm> {
            val form = req.formAsMap()
            
            return Validation<CreateBookForm> {
                CreateBookForm::title required { notEmpty() }
                CreateBookForm::author required { notEmpty() }
                CreateBookForm::libraryId required { min(1) }
            }.validate(CreateBookForm(
                title = form["title"]?.firstOrNull() ?: "",
                author = form["author"]?.firstOrNull() ?: "",
                isbn = form["isbn"]?.firstOrNull(),
                libraryId = form["libraryId"]?.firstOrNull()?.toLongOrNull() ?: 0
            ))
        }
    }
}
```

```html
<!-- Form with HTMX -->
<form hx-post="/books" hx-target="#result" hx-swap="outerHTML">
    <div class="field">
        <label>Title</label>
        <input type="text" name="title" required>
        {% if errors.title %}
        <span class="error">{{ errors.title }}</span>
        {% endif %}
    </div>
    
    <div class="field">
        <label>Author</label>
        <input type="text" name="author" required>
    </div>
    
    <button type="submit">Create Book</button>
</form>
```

### 7. Reader Integration

```html
<!-- templates/readers/pdf-reader.html -->
{% extends "layouts/reader.html" %}

{% block reader %}
<div id="pdf-container" data-book-id="{{ book.id }}">
    <div class="toolbar">
        <button onclick="pdfViewer.prevPage()">Previous</button>
        <span id="page-num"></span> / <span id="page-count"></span>
        <button onclick="pdfViewer.nextPage()">Next</button>
        <button onclick="pdfViewer.zoomIn()">+</button>
        <button onclick="pdfViewer.zoomOut()">-</button>
    </div>
    <canvas id="pdf-canvas"></canvas>
</div>

<script src="/static/js/pdf-viewer.js"></script>
<script>
    const pdfViewer = new PDFViewer({
        bookId: '{{ book.id }}',
        initialPage: {{ progress.currentPage }},
        container: document.getElementById('pdf-container')
    });
    
    // Save progress via HTMX
    pdfViewer.onPageChange = function(page) {
        htmx.ajax('POST', '/api/progress', {
            values: { bookId: '{{ book.id }}', page: page }
        });
    };
</script>
{% endblock %}
```

## Data Flow Comparison

### Angular (Current)
```
Browser → HTTP GET /api/v1/books → JSON Response
    ↓
Angular processes JSON → Updates state → Re-renders DOM
    ↓
User clicks → HTTP POST /api/v1/books → JSON Response
    ↓
Update state → Re-render
```

### http4k + HTMX (Target)
```
Browser → HTTP GET /books → HTML Response (full page)
    ↓
Browser renders HTML directly
    ↓
User clicks → HTTP GET/POST → HTML Response (partial)
    ↓
HTMX swaps partial into DOM
```

## Key Patterns

### 1. Progressive Enhancement
Every feature works without JavaScript, enhanced with HTMX:
- Forms submit normally, enhanced with `hx-post`
- Links work normally, enhanced with `hx-get`
- Search works normally, enhanced with `hx-trigger`

### 2. Partial Page Updates
Use `HX-Request` header to detect HTMX:
```kotlin
if (req.header("HX-Request") != null) {
    // Return partial HTML
} else {
    // Return full page
}
```

### 3. Toast Notifications
```html
<div id="toast-container" hx-swap-oob="true">
    {% if toast %}
    <div class="toast">{{ toast.message }}</div>
    {% endif %}
</div>
```

### 4. Loading States
```html
<button hx-post="/books" hx-indicator="#loading">
    Save
</button>
<div id="loading" class="htmx-indicator">Saving...</div>
```

## Migration Risks & Mitigation

### Risks

1. **Complexity of Readers**
   - PDF, EPUB, CBX readers are complex
   - Risk: May not work as smoothly
   - Mitigation: Keep existing reader libraries, wrap with HTMX

2. **Real-time Features**
   - WebSocket → SSE transition
   - Risk: SSE less efficient for high-frequency updates
   - Mitigation: SSE is sufficient for BookLore's use case

3. **Performance**
   - Server rendering vs client rendering
   - Risk: Slower initial loads
   - Mitigation: Caching, CDN, optimized templates

4. **Team Learning Curve**
   - Kotlin + http4k + HTMX
   - Risk: Development slowdown
   - Mitigation: Good documentation, training

5. **SEO Concerns**
   - Actually improved with SSR
   - Risk: None

### Rollback Plan
- Keep Angular app deployable
- Feature flags to switch between frontends
- Gradual rollout (canary deployment)

## Success Criteria

1. **Performance**
   - Time to First Byte (TTFB) < 200ms
   - Total page load < 2s
   - Bundle size < 500KB (HTMX + CSS)

2. **Functionality**
   - All current features working
   - Readers functional
   - Real-time updates working

3. **Maintainability**
   - < 50 Kotlin files (vs 404 TypeScript)
   - < 20 templates
   - Clear separation of concerns

4. **User Experience**
   - No functionality loss
   - Improved perceived performance
   - Better SEO

## Conclusion

This migration moves BookLore from a complex SPA to a simpler, more maintainable hypermedia-driven architecture. While significant work, the benefits include:

- Reduced complexity (10x fewer source files)
- Better performance
- Improved SEO
- Easier maintenance
- Progressive enhancement

The phased approach minimizes risk while delivering value incrementally.
