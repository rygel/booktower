# Moving Business Logic to Backend: Server-Driven Reader Architecture

## Executive Summary

**Yes, absolutely!** This is actually the key insight that makes the HTMX migration compelling. By moving business logic to the backend, you transform complex Angular SPAs into simple HTML renderers.

**New Architecture:**
```
Backend (http4k/Kotlin)          Frontend (HTMX + Vanilla JS)
├─ Business logic                ├─ Render content (PDF.js/Foliate)
├─ State management              ├─ Capture user actions
├─ Bookmarks/Annotations         ├─ Send events to backend
├─ Progress tracking             ├─ Display backend data
└─ User preferences              └─ Minimal local state only
```

## Why This Changes Everything

### Current Angular Architecture
```
Client (Complex)
├─ PDF.js/Foliate (rendering)
├─ Business logic (Angular services)
│  ├─ BookmarkService (CRUD + state)
│  ├─ AnnotationService (CRUD + state)
│  ├─ ProgressService (tracking + sync)
│  └─ SettingsService (preferences)
└─ State management (RxJS/BehaviorSubjects)
```

### Proposed Server-Driven Architecture
```
Backend (http4k/Kotlin)
├─ BookmarkHandler (REST endpoints)
├─ AnnotationHandler (REST endpoints)
├─ ProgressHandler (REST endpoints)
└─ SettingsHandler (REST endpoints)

Client (Simple)
├─ PDF.js/Foliate (rendering only)
├─ HTMX (server communication)
└─ Vanilla JS (event capture only)
```

## Concrete Examples

### 1. Bookmarks

**Current Angular Way:**
```typescript
// Angular Service
@Injectable()
class BookmarkService {
  private bookmarks$ = new BehaviorSubject<Bookmark[]>([]);
  
  async addBookmark(bookId: number, page: number, text: string) {
    const bookmark = await this.http.post('/api/bookmarks', {bookId, page, text});
    this.bookmarks$.next([...this.bookmarks$.value, bookmark]);
    // Update UI, sync state, etc.
  }
  
  getBookmarks(bookId: number): Observable<Bookmark[]> {
    return this.bookmarks$.pipe(
      map(b => b.filter(b => b.bookId === bookId))
    );
  }
}
```

**New Server-Driven Way:**
```kotlin
// Backend Handler
fun bookmarkRoutes(bookmarkService: BookmarkService): HttpHandler = routes(
    "/api/bookmarks/{bookId}" bind GET to { req ->
        val bookId = req.path("bookId")!!.toLong()
        val bookmarks = bookmarkService.getBookmarks(bookId)
        
        // Return HTML partial for HTMX
        Response(OK).body(renderBookmarkList(bookmarks))
    },
    
    "/api/bookmarks" bind POST to { req ->
        val form = req.formAsMap()
        val bookmark = bookmarkService.createBookmark(
            bookId = form["bookId"]!!.toLong(),
            page = form["page"]!!.toInt(),
            text = form["text"]!!
        )
        
        // Return updated bookmark list
        val bookmarks = bookmarkService.getBookmarks(bookmark.bookId)
        Response(OK)
            .header("HX-Trigger", "{\"showToast\": \"Bookmark added\"}")
            .body(renderBookmarkList(bookmarks))
    },
    
    "/api/bookmarks/{id}" bind DELETE to { req ->
        val id = req.path("id")!!.toLong()
        bookmarkService.deleteBookmark(id)
        Response(OK).body("") // HTMX will remove element
    }
)
```

```html
<!-- Frontend Template -->
<div id="bookmark-sidebar" hx-get="/api/bookmarks/{{ book.id }}" hx-trigger="load">
  <!-- Bookmarks loaded from server -->
</div>

<!-- Bookmark creation triggered by reader -->
<script>
  // When user adds bookmark in reader
  function onBookmarkAdd(page, text) {
    htmx.ajax('POST', '/api/bookmarks', {
      values: { bookId: {{ book.id }}, page: page, text: text },
      target: '#bookmark-sidebar'
    });
  }
</script>
```

**Benefits:**
- ✅ No state management in JS
- ✅ Server is source of truth
- ✅ Instant sync across devices
- ✅ Simple HTML rendering

---

### 2. Annotations

**Current Angular Way:**
```typescript
// Complex annotation system
class AnnotationService {
  private annotations$ = new BehaviorSubject<Annotation[]>([]);
  private pendingSaves = new Map<string, Promise<void>>();
  
  async saveAnnotation(annotation: Annotation) {
    // Debounce, optimistic updates, conflict resolution...
  }
}
```

**New Server-Driven Way:**
```kotlin
// Backend
class AnnotationHandler(private val annotationService: AnnotationService) {
    
    fun create(req: Request): Response {
        val form = req.formAsMap()
        val annotation = annotationService.create(
            bookId = form["bookId"]!!.toLong(),
            page = form["page"]!!.toInt(),
            x = form["x"]!!.toDouble(),
            y = form["y"]!!.toDouble(),
            text = form["text"]!!
        )
        
        // Return HTML for the annotation marker
        Response(OK).body(renderAnnotationMarker(annotation))
    }
    
    fun list(req: Request): Response {
        val bookId = req.path("bookId")!!.toLong()
        val annotations = annotationService.getForBook(bookId)
        
        // Return as JSON for reader to display
        Response(OK)
            .header("Content-Type", "application/json")
            .body(jackson.writeValueAsString(annotations))
    }
}
```

```html
<!-- Frontend -->
<script>
  // Load annotations when page opens
  async function loadAnnotations() {
    const response = await fetch('/api/annotations/{{ book.id }}');
    const annotations = await response.json();
    
    // Render in PDF.js/Foliate
    annotations.forEach(ann => {
      renderAnnotationMarker(ann.x, ann.y, ann.text);
    });
  }
  
  // Save annotation
  function onAnnotationCreate(x, y, text) {
    htmx.ajax('POST', '/api/annotations', {
      values: { bookId: {{ book.id }}, x, y, text, page: currentPage }
    });
  }
</script>
```

---

### 3. Reading Progress

**Current Angular Way:**
```typescript
class ProgressService {
  private saveDebounce$ = new Subject<Progress>();
  
  constructor() {
    this.saveDebounce$.pipe(
      debounceTime(5000)
    ).subscribe(p => this.saveToServer(p));
  }
  
  updateProgress(bookId: number, page: number) {
    this.saveDebounce$.next({bookId, page, timestamp: Date.now()});
  }
}
```

**New Server-Driven Way:**
```kotlin
// Backend - no complex logic needed
fun progressRoutes(progressService: ProgressService) = routes(
    "/api/progress/{bookId}" bind GET to { req ->
        val bookId = req.path("bookId")!!.toLong()
        val progress = progressService.getProgress(bookId)
        
        Response(OK)
            .header("Content-Type", "application/json")
            .body("""{"page": ${progress?.page ?: 1}, "percentage": ${progress?.percentage ?: 0}}""")
    },
    
    "/api/progress" bind POST to { req ->
        val form = req.formAsMap()
        progressService.saveProgress(
            bookId = form["bookId"]!!.toLong(),
            page = form["page"]!!.toInt(),
            percentage = form["percentage"]?.toDoubleOrNull()
        )
        Response(OK).body("") // Just acknowledge
    }
)
```

```html
<!-- Frontend - Simple debouncing in vanilla JS -->
<script>
  let progressTimeout;
  
  function onPageChange(page) {
    clearTimeout(progressTimeout);
    progressTimeout = setTimeout(() => {
      fetch('/api/progress', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `bookId={{ book.id }}&page=${page}`
      });
    }, 5000); // 5 second debounce
  }
</script>
```

**Key insight:** The debouncing is still in JS (client-side concern), but storage and business logic is on server.

---

### 4. User Settings

**Current Angular Way:**
```typescript
class SettingsService {
  private settings$ = new BehaviorSubject<UserSettings>(defaultSettings);
  
  async loadSettings() {
    const settings = await this.http.get('/api/settings').toPromise();
    this.settings$.next(settings);
  }
  
  async updateSetting(key: string, value: any) {
    // Optimistic update, rollback on error, etc.
  }
}
```

**New Server-Driven Way:**
```kotlin
// Backend
class SettingsHandler(private val settingsService: SettingsService) {
    
    fun get(req: Request): Response {
        val userId = req.authenticatedUser().id
        val settings = settingsService.getSettings(userId)
        
        // Return as HTML form
        Response(OK).body(renderSettingsForm(settings))
    }
    
    fun update(req: Request): Response {
        val form = req.formAsMap()
        val userId = req.authenticatedUser().id
        
        form.forEach { (key, values) ->
            settingsService.updateSetting(userId, key, values.first())
        }
        
        Response(SEE_OTHER).header("Location", "/settings")
    }
}
```

```html
<!-- Frontend - Standard HTML form with HTMX -->
<form hx-post="/api/settings" hx-swap="none">
  <label>Theme</label>
  <select name="theme" hx-post="/api/settings/theme" hx-trigger="change">
    <option value="light" {% if settings.theme == 'light' %}selected{% endif %}>Light</option>
    <option value="dark" {% if settings.theme == 'dark' %}selected{% endif %}>Dark</option>
  </select>
  
  <label>Font Size</label>
  <input type="range" name="fontSize" min="12" max="24" 
         value="{{ settings.fontSize }}"
         hx-post="/api/settings/font-size"
         hx-trigger="change">
</form>
```

---

## Complete Reader Architecture

### PDF Reader with Backend Logic

```kotlin
// Backend: Runary Web (http4k)

// 1. Serve reader page with initial state
fun pdfReaderPage(bookId: Long, userId: String): String {
    val book = bookService.getBook(bookId)
    val progress = progressService.getProgress(userId, bookId)
    val annotations = annotationService.getForBook(userId, bookId)
    val bookmarks = bookmarkService.getForBook(userId, bookId)
    
    return renderTemplate("readers/pdf-reader.html", mapOf(
        "book" to book,
        "progress" to progress,
        "annotations" to jackson.writeValueAsString(annotations),
        "bookmarks" to bookmarks
    ))
}

// 2. Handle reader events
fun readerEventRoutes() = routes(
    // Page change
    "/api/readers/event/page-change" bind POST to { req ->
        val form = req.formAsMap()
        val userId = req.authenticatedUser().id
        val bookId = form["bookId"]!!.toLong()
        val page = form["page"]!!.toInt()
        
        // Save progress
        progressService.saveProgress(userId, bookId, page)
        
        // Check for bookmarks on this page
        val bookmarks = bookmarkService.getForPage(userId, bookId, page)
        
        // Return any UI updates needed
        Response(OK)
            .header("HX-Trigger", jackson.writeValueAsString(mapOf(
                "bookmarksOnPage" to bookmarks.map { it.text }
            )))
            .body("")
    },
    
    // Annotation created
    "/api/readers/event/annotation" bind POST to { req ->
        val form = req.formAsMap()
        val annotation = annotationService.create(
            userId = req.authenticatedUser().id,
            bookId = form["bookId"]!!.toLong(),
            page = form["page"]!!.toInt(),
            x = form["x"]!!.toDouble(),
            y = form["y"]!!.toDouble(),
            text = form["text"]!!
        )
        
        // Return the annotation with ID for client to display
        Response(OK)
            .header("Content-Type", "application/json")
            .body(jackson.writeValueAsString(annotation))
    }
)
```

```html
<!-- Frontend: pdf-reader.html -->
<!DOCTYPE html>
<html>
<head>
  <title>{{ book.title }} - PDF Reader</title>
  <script src="/assets/pdfjs/pdf.min.js"></script>
  <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body>
  <div id="reader-container" 
       data-book-id="{{ book.id }}"
       data-initial-page="{{ progress.page }}">
    
    <!-- PDF.js Canvas -->
    <canvas id="pdf-canvas"></canvas>
    
    <!-- Bookmark Sidebar (loaded from server) -->
    <div id="bookmark-sidebar" 
         hx-get="/api/bookmarks/{{ book.id }}"
         hx-trigger="load">
    </div>
    
    <!-- Toolbar -->
    <div class="toolbar">
      <button onclick="pdfViewer.prevPage()">Previous</button>
      <span id="page-indicator">{{ progress.page }} / {{ book.totalPages }}</span>
      <button onclick="pdfViewer.nextPage()">Next</button>
      <button onclick="addBookmark()">Add Bookmark</button>
    </div>
  </div>

  <script>
    // 1. Load PDF.js
    const bookId = {{ book.id }};
    const annotations = {{ annotations | raw }}; // JSON array from server
    let currentPage = {{ progress.page }};
    
    // 2. Initialize PDF.js
    pdfjsLib.getDocument('/api/v1/books/{{ book.id }}/content').promise.then(pdf => {
      window.pdfDoc = pdf;
      renderPage(currentPage);
      renderAnnotations(); // Use server-provided annotation data
    });
    
    // 3. Render page
    function renderPage(pageNum) {
      pdfDoc.getPage(pageNum).then(page => {
        const viewport = page.getViewport({ scale: 1.5 });
        const canvas = document.getElementById('pdf-canvas');
        const context = canvas.getContext('2d');
        canvas.height = viewport.height;
        canvas.width = viewport.width;
        page.render({ canvasContext: context, viewport: viewport });
        
        currentPage = pageNum;
        document.getElementById('page-indicator').textContent = `${pageNum} / ${pdfDoc.numPages}`;
        
        // 4. Report to backend (debounced)
        reportPageChange(pageNum);
      });
    }
    
    // 5. Report page changes to backend
    let pageChangeTimeout;
    function reportPageChange(page) {
      clearTimeout(pageChangeTimeout);
      pageChangeTimeout = setTimeout(() => {
        fetch('/api/readers/event/page-change', {
          method: 'POST',
          headers: {'Content-Type': 'application/x-www-form-urlencoded'},
          body: `bookId=${bookId}&page=${page}`
        });
      }, 3000);
    }
    
    // 6. Add bookmark (sends to backend)
    function addBookmark() {
      const text = prompt('Bookmark note:');
      if (text) {
        htmx.ajax('POST', '/api/bookmarks', {
          values: { bookId: bookId, page: currentPage, text: text },
          target: '#bookmark-sidebar'
        });
      }
    }
    
    // 7. Render annotations (using server data)
    function renderAnnotations() {
      annotations.forEach(ann => {
        if (ann.page === currentPage) {
          renderAnnotationMarker(ann.x, ann.y, ann.text);
        }
      });
    }
  </script>
</body>
</html>
```

---

## What Stays in JavaScript

Even with backend-driven logic, some things must stay client-side:

### 1. Rendering
- PDF.js canvas rendering
- Foliate.js view management
- Canvas drawing for CBX
- Audio playback (Howler.js)

### 2. Event Capture
- Page turn detection
- Text selection
- Click coordinates for annotations
- Scroll/resize events

### 3. Timing/Delays
- Debouncing progress saves
- Throttling scroll events
- Animation timing

### 4. Temporary State
- Currently visible page
- Zoom level (until saved)
- Open menus/panels

### 5. Third-Party Library Integration
- PDF.js initialization
- Foliate.js lifecycle
- Chart rendering

---

## API Design for Server-Driven Readers

```kotlin
// REST API for reader state
interface ReaderApi {
    
    // Get reader page with all initial data
    GET /readers/pdf/{bookId} -> HTML page
    GET /readers/epub/{bookId} -> HTML page
    GET /readers/cbx/{bookId} -> HTML page
    GET /readers/audio/{bookId} -> HTML page
    
    // Progress
    GET /api/readers/progress/{bookId} -> {page, percentage, lastRead}
    POST /api/readers/progress -> save progress
    
    // Bookmarks
    GET /api/bookmarks/{bookId} -> HTML partial or JSON
    POST /api/bookmarks -> create
    DELETE /api/bookmarks/{id} -> delete
    
    // Annotations
    GET /api/annotations/{bookId} -> JSON array
    POST /api/annotations -> create
    PUT /api/annotations/{id} -> update
    DELETE /api/annotations/{id} -> delete
    
    // Settings (per book or global)
    GET /api/readers/settings/{bookId} -> JSON
    POST /api/readers/settings -> update
    
    // Events (for analytics/sync)
    POST /api/readers/event/{eventType} -> log event
}
```

---

## Database Schema Additions

```sql
-- Reader progress (already exists?)
CREATE TABLE reading_progress (
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    current_page INT DEFAULT 1,
    percentage DECIMAL(5,2) DEFAULT 0,
    last_read TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, book_id)
);

-- Bookmarks
CREATE TABLE bookmarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    page INT NOT NULL,
    text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_book (user_id, book_id)
);

-- Annotations
CREATE TABLE annotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    page INT NOT NULL,
    x_coordinate DECIMAL(10,2),
    y_coordinate DECIMAL(10,2),
    width DECIMAL(10,2),
    height DECIMAL(10,2),
    text TEXT,
    color VARCHAR(7) DEFAULT '#FFFF00',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_user_book_page (user_id, book_id, page)
);

-- Reader settings (per user, per book or global)
CREATE TABLE reader_settings (
    user_id BIGINT NOT NULL,
    book_id BIGINT, -- NULL = global default
    setting_key VARCHAR(50) NOT NULL,
    setting_value TEXT,
    PRIMARY KEY (user_id, book_id, setting_key)
);
```

---

## Benefits of Server-Driven Architecture

### 1. **Simplified Frontend**
- No state management libraries
- No RxJS, no BehaviorSubjects
- HTML + minimal JS only
- 10x reduction in frontend code

### 2. **Single Source of Truth**
- Server always has correct state
- No sync conflicts
- Instant cross-device updates

### 3. **Better Security**
- Business logic validation on server
- No sensitive data in client
- Proper authorization checks

### 4. **Easier Testing**
- Test business logic in Kotlin
- No complex Angular testing
- Simple HTTP endpoint tests

### 5. **Offline Capability**
- Can add service workers later
- Cache HTML pages
- Queue actions when offline

### 6. **SEO & Performance**
- Server-rendered HTML
- Faster initial load
- Better search indexing

---

## Migration Path for Business Logic

### Phase 1: Expose Current APIs (Week 1-2)
- Keep Angular app running
- Create http4k proxy to Spring Boot
- Ensure all endpoints work

### Phase 2: Move State to Backend (Week 3-6)
- Create new tables (bookmarks, annotations)
- Migrate existing data
- Update Spring Boot controllers
- Test with Angular

### Phase 3: Build Server-Driven UI (Week 7-12)
- Create HTML templates
- Build HTMX handlers
- Implement vanilla JS readers
- Add backend business logic

### Phase 4: Switch Over (Week 13-14)
- Route traffic to new frontend
- Monitor errors
- Fix issues

### Phase 5: Cleanup (Week 15-16)
- Remove Angular services
- Delete dead code
- Optimize backend

---

## Code Comparison: Before & After

### Before (Angular): ~500 lines
```typescript
@Component({...})
class PdfReaderComponent {
  bookmarks$ = new BehaviorSubject<Bookmark[]>([]);
  annotations$ = new BehaviorSubject<Annotation[]>([]);
  progress$ = new BehaviorSubject<Progress>({});
  settings$ = new BehaviorSubject<Settings>({});
  
  constructor(
    private bookmarkService: BookmarkService,
    private annotationService: AnnotationService,
    private progressService: ProgressService,
    private settingsService: SettingsService
  ) {}
  
  ngOnInit() {
    // Complex RxJS orchestration
    combineLatest([
      this.bookmarkService.getBookmarks(this.bookId),
      this.annotationService.getAnnotations(this.bookId),
      this.progressService.getProgress(this.bookId)
    ]).subscribe(([bookmarks, annotations, progress]) => {
      this.bookmarks$.next(bookmarks);
      this.annotations$.next(annotations);
      this.progress$.next(progress);
      this.initializeReader();
    });
  }
  
  onPageChange(page: number) {
    this.progressService.updateProgress(this.bookId, page);
    this.loadAnnotationsForPage(page);
    this.checkBookmarksForPage(page);
  }
  
  onAnnotationCreate(x: number, y: number, text: string) {
    this.annotationService.create({
      bookId: this.bookId,
      page: this.currentPage,
      x, y, text
    }).subscribe(annotation => {
      const current = this.annotations$.value;
      this.annotations$.next([...current, annotation]);
    });
  }
  
  // ... 200 more lines of business logic
}
```

### After (Server-Driven): ~50 lines
```html
<!-- Template receives data from server -->
<div id="reader" 
     data-book-id="{{ book.id }}"
     data-initial-page="{{ progress.page }}"
     data-annotations="{{ annotations | json }}">
  <canvas id="pdf-canvas"></canvas>
</div>

<script>
  // Just rendering and event capture
  const bookId = {{ book.id }};
  const annotations = {{ annotations | raw }};
  
  // Render PDF
  pdfjsLib.getDocument(url).promise.then(pdf => {
    window.pdfDoc = pdf;
    renderPage({{ progress.page }});
  });
  
  // Report events to backend
  function onPageChange(page) {
    fetch('/api/readers/event/page-change', {
      method: 'POST',
      body: `bookId=${bookId}&page=${page}`
    });
  }
</script>
```

---

## Conclusion

**Moving business logic to the backend is not only possible - it's the recommended approach!**

This transforms Runary from a complex SPA into a simple hypermedia application:

- **Backend**: Rich domain logic, state management, data persistence
- **Frontend**: HTML rendering, event capture, minimal local state

**Result:**
- 90% reduction in frontend code
- Simpler architecture
- Better performance
- Easier maintenance
- Progressive enhancement by default

The readers become "smart clients, dumb renderers" - they display content and report user actions, while the backend handles all business logic and state management.
