# Full Rewrite Plan: MVP-First Approach

## Executive Summary

**Strategy:** Build BookTower v2 from scratch using http4k + HTMX, launching an MVP first, then iterating with user feedback.

**Timeline:** 12-16 months total, but **MVP launches at Month 4**

**Key Principle:** Ship early, ship often, get user feedback

---

## MVP Definition (Minimum Viable Product)

### What is the MVP?

The MVP is a **working e-book library** that allows users to:
- Upload and organize PDF books
- View PDFs in a browser
- Track basic reading progress
- Simple user management

**Not in MVP:** EPUB/CBX/Audio readers, complex sync, annotations, metadata fetching, etc.

### Why This MVP?

**Target Users:**
- Personal e-book collectors
- Small libraries
- Users who primarily read PDFs
- Users who want simple, fast library management

**Core Value Proposition:**
"A fast, simple, self-hosted PDF library with basic reading capabilities"

---

## Feature Prioritization Matrix

### Must Have (MVP - Month 4)

| Feature | Priority | Effort | User Value | Why Critical |
|---------|----------|--------|------------|--------------|
| **Authentication** | P0 | Medium | Critical | Can't use app without login |
| **Library CRUD** | P0 | Medium | Critical | Core functionality |
| **Book Upload** | P0 | Medium | Critical | Core functionality |
| **PDF Viewer** | P0 | High | Critical | Core reading experience |
| **Basic Progress** | P0 | Low | High | Track where you left off |
| **Book Metadata** | P0 | Medium | Medium | Search, display |
| **User Settings** | P0 | Low | Medium | Personalization |

### Should Have (Phase 2 - Month 6-8)

| Feature | Priority | Effort | User Value | Why Important |
|---------|----------|--------|------------|---------------|
| **EPUB Reader** | P1 | High | Critical | 2nd most popular format |
| **Metadata Fetching** | P1 | Medium | High | Auto-fill book info |
| **Book Covers** | P1 | Low | High | Visual browsing |
| **Search & Filter** | P1 | Medium | High | Find books easily |
| **Basic Bookmarks** | P1 | Low | High | Save positions |
| **Library Scan** | P1 | Medium | Medium | Auto-import existing files |
| **File Organization** | P1 | Low | Medium | Keep library tidy |

### Could Have (Phase 3 - Month 9-12)

| Feature | Priority | Effort | User Value | Notes |
|---------|----------|--------|------------|-------|
| **CBX Reader** | P2 | High | Medium | Comic books |
| **Audiobook Player** | P2 | High | Medium | Audio books |
| **Annotations** | P2 | High | Medium | Markup PDFs |
| **Magic Shelves** | P2 | Medium | Medium | Smart collections |
| **Statistics** | P2 | Medium | Low | Nice to have |
| **OPDS Feed** | P2 | Medium | Low | E-reader integration |
| **Email Notifications** | P2 | Medium | Low | Alerts |

### Won't Have (Phase 4+ - Month 13+)

| Feature | Priority | Notes |
|---------|----------|-------|
| **Kobo Sync** | P3 | Complex integration |
| **KOReader Sync** | P3 | Complex integration |
| **Hardcover Sync** | P3 | External service |
| **Advanced Analytics** | P3 | Future enhancement |
| **Mobile Apps** | P3 | Separate project |
| **Collaborative Features** | P3 | Multi-user features |

---

## Detailed MVP Feature Set

### 1. Authentication (Week 1-2)

**Features:**
- User registration
- Login/logout
- JWT token-based auth
- Password reset
- Basic user profile

**Out of Scope:**
- OAuth2/OIDC (add later)
- Social login (add later)
- Multi-factor auth (add later)
- Admin roles (add later)

**Technical:**
```kotlin
// Single user table
@Entity
@Table(name = "users")
data class User(
    @Id val id: UUID = UUID.randomUUID(),
    val username: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant = Instant.now(),
    val isAdmin: Boolean = false
)

// JWT implementation
class JwtAuthenticator {
    fun generateToken(user: User): String { ... }
    fun validateToken(token: String): User? { ... }
}
```

---

### 2. Library Management (Week 3-4)

**Features:**
- Create library
- Edit library name/path
- Delete library
- List libraries
- Library selector

**Out of Scope:**
- Folder-based libraries (add later)
- Multiple paths per library (add later)
- Library templates (add later)

**Technical:**
```kotlin
@Entity
@Table(name = "libraries")
data class Library(
    @Id val id: UUID = UUID.randomUUID(),
    val name: String,
    val path: String,
    val createdAt: Instant = Instant.now()
)
```

---

### 3. Book Upload & Management (Week 5-7)

**Features:**
- Upload PDF files
- Extract metadata (title, author from PDF)
- Display book list (title, author, cover)
- Book detail page
- Delete books
- Move between libraries

**Out of Scope:**
- Batch upload (add later)
- EPUB/CBX/Audio support (add later)
- Metadata fetching from external sources (add later)
- Duplicate detection (add later)

**Technical:**
```kotlin
@Entity
@Table(name = "books")
data class Book(
    @Id val id: UUID = UUID.randomUUID(),
    val libraryId: UUID,
    val title: String,
    val author: String?,
    val description: String?,
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int?,
    val addedAt: Instant = Instant.now()
)

@Entity
@Table(name = "book_files")
data class BookFile(
    @Id val id: UUID = UUID.randomUUID(),
    val bookId: UUID,
    val filePath: String,
    val format: String, // "pdf", "epub", etc.
    val fileSize: Long
)
```

---

### 4. PDF Reader (Week 8-10) - MOST CRITICAL

**Features:**
- View PDF in browser
- Page navigation (prev/next, go to page)
- Zoom (fit width, fit page, custom)
- Remember last page read
- Keyboard shortcuts (arrow keys, space)
- Basic toolbar

**Out of Scope:**
- Annotations (add later)
- Text search (add later)
- Text selection (add later)
- Thumbnails (add later)
- Bookmarks within PDF (add later)

**Technical:**
```html
<!-- Minimal PDF viewer -->
<div id="pdf-container" data-book-id="{{ book.id }}">
  <div class="toolbar">
    <button onclick="prevPage()">←</button>
    <span id="page-num">{{ progress.page }}</span> / <span id="page-count">{{ book.pageCount }}</span>
    <button onclick="nextPage()">→</button>
    <button onclick="zoomIn()">+</button>
    <button onclick="zoomOut()">-</button>
  </div>
  <canvas id="pdf-canvas"></canvas>
</div>

<script src="/assets/pdfjs/pdf.min.js"></script>
<script>
  let pdfDoc, currentPage = {{ progress.page }};
  
  pdfjsLib.getDocument('/api/books/{{ book.id }}/content').promise.then(pdf => {
    pdfDoc = pdf;
    renderPage(currentPage);
  });
  
  function renderPage(num) {
    pdfDoc.getPage(num).then(page => {
      const viewport = page.getViewport({ scale: 1.5 });
      const canvas = document.getElementById('pdf-canvas');
      canvas.height = viewport.height;
      canvas.width = viewport.width;
      page.render({ canvasContext: canvas.getContext('2d'), viewport });
      currentPage = num;
      
      // Save progress (debounced)
      saveProgress(num);
    });
  }
</script>
```

---

### 5. Reading Progress (Week 10-11)

**Features:**
- Track current page per book
- Display "Last read: Page X" in book list
- Continue reading button

**Out of Scope:**
- Reading time tracking (add later)
- Percentage complete (add later)
- Reading statistics (add later)
- Cross-device sync (add later)

**Technical:**
```kotlin
@Entity
@Table(name = "reading_progress")
data class ReadingProgress(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val bookId: UUID,
    val currentPage: Int,
    val updatedAt: Instant = Instant.now()
)
```

---

### 6. Basic UI/UX (Week 12-14)

**Features:**
- Clean book grid view
- Book detail page
- Library selector
- User menu
- Settings page
- Responsive design

**Out of Scope:**
- Dark mode (add later)
- Themes (add later)
- Advanced layouts (add later)
- Drag & drop (add later)

**Design:**
```
┌─────────────────────────────────────┐
│ BookTower    [Library ▼] [User ▼]   │
├─────────────────────────────────────┤
│                                     │
│  ┌──────┐  ┌──────┐  ┌──────┐     │
│  │Book 1│  │Book 2│  │Book 3│     │
│  │Cover │  │Cover │  │Cover │     │
│  │Title │  │Title │  │Title │     │
│  │Author│  │Author│  │Author│     │
│  └──────┘  └──────┘  └──────┘     │
│                                     │
│  ┌──────┐  ┌──────┐  ┌──────┐     │
│  │Book 4│  │Book 5│  │Book 6│     │
│  │Cover │  │Cover │  │Cover │     │
│  │Title │  │Title │  │Title │     │
│  │Author│  │Author│  │Author│     │
│  └──────┘  └──────┘  └──────┘     │
│                                     │
└─────────────────────────────────────┘
```

---

### 7. Deployment (Week 15-16)

**Features:**
- Docker container
- Docker Compose setup
- Basic documentation
- Health checks

**Out of Scope:**
- Kubernetes (add later)
- Monitoring (add later)
- Backups (add later)
- CDN (add later)

---

## MVP Architecture

### Simplified Architecture for MVP

```
┌─────────────────────────────────────────────────────┐
│  BookTower MVP (http4k + HTMX)                       │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  HTTP Layer (http4k)                         │  │
│  │  ├─ AuthHandler                               │  │
│  │  ├─ LibraryHandler                            │  │
│  │  ├─ BookHandler                               │  │
│  │  ├─ ReaderHandler                             │  │
│  │  └─ ProgressHandler                           │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  Service Layer                               │  │
│  │  ├─ AuthService                              │  │
│  │  ├─ LibraryService                           │  │
│  │  ├─ BookService                              │  │
│  │  ├─ FileService                              │  │
│  │  └─ ProgressService                          │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  Data Layer (Exposed)                        │  │
│  │  ├─ UserRepository                           │  │
│  │  ├─ LibraryRepository                       │  │
│  │  ├─ BookRepository                           │  │
│  │  └─ ProgressRepository                      │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  File Storage                                │  │
│  │  └─ Local filesystem                         │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────┐
│  Database (MariaDB/PostgreSQL)                      │
│  ├─ users                                           │
│  ├─ libraries                                       │
│  ├─ books                                           │
│  └─ reading_progress                                │
└─────────────────────────────────────────────────────┘
```

---

## Database Schema (MVP)

### Minimal Schema (6 tables)

```sql
-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Libraries
CREATE TABLE libraries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Books
CREATE TABLE books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    library_id UUID REFERENCES libraries(id),
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    description TEXT,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    page_count INT,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Reading Progress
CREATE TABLE reading_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    book_id UUID REFERENCES books(id),
    current_page INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, book_id)
);

-- Settings
CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    setting_key VARCHAR(50) NOT NULL,
    setting_value TEXT,
    UNIQUE(user_id, setting_key)
);
```

**Total tables: 5** (vs 40+ in current BookTower)

---

## MVP Development Phases

### Phase 0: Setup (Week 1)

**Goals:**
- Project structure
- Database setup
- Basic HTTP server
- First "Hello World"

**Tasks:**
1. Create Kotlin project with Gradle
2. Add http4k dependencies
3. Set up Exposed for database
4. Create basic route
5. Verify database connection
6. Set up Pebble templates
7. Create base layout

**Deliverable:** Running server that serves HTML page

---

### Phase 1: Foundation (Week 2-3)

**Goals:**
- User authentication
- Library management

**Week 2: Authentication**
- User registration form
- Login form
- JWT implementation
- Password hashing
- Protected routes
- Logout

**Week 3: Libraries**
- Library creation form
- Library list
- Library edit
- Library delete
- Library selector

**Deliverable:** Users can register, login, and create libraries

---

### Phase 2: Core (Week 4-7)

**Goals:**
- Book upload
- Book management
- Basic metadata

**Week 4: Book Upload**
- File upload form
- Store files in library path
- Basic metadata extraction (PDF title)
- Book entity creation

**Week 5: Book List**
- Display books in grid
- Book cards with title, author
- Basic styling
- Pagination

**Week 6: Book Detail**
- Book detail page
- Metadata display
- File download
- Book deletion

**Week 7: Polish**
- Error handling
- Validation
- Loading states
- Responsive design

**Deliverable:** Users can upload books and view library

---

### Phase 3: Reader (Week 8-11)

**Goals:**
- PDF viewer
- Progress tracking

**Week 8: PDF Integration**
- Add PDF.js library
- Serve PDF content
- Basic PDF display

**Week 9: Navigation**
- Page navigation (prev/next)
- Page counter
- Keyboard shortcuts
- Scroll/zoom

**Week 10: Progress**
- Save current page
- Load last page
- Continue reading button
- Progress indicator

**Week 11: Polish**
- Loading states
- Error handling
- Mobile responsive
- Touch support

**Deliverable:** Users can read PDFs and track progress

---

### Phase 4: Launch Prep (Week 12-16)

**Goals:**
- Polish UI
- Testing
- Documentation
- Deployment

**Week 12-13: UI Polish**
- Consistent styling
- Dark mode (optional)
- Animations/transitions
- Icons

**Week 14: Testing**
- Manual testing
- Bug fixes
- Edge cases

**Week 15: Documentation**
- README
- Installation guide
- User guide

**Week 16: Deployment**
- Docker setup
- Docker Compose
- Basic monitoring
- Launch!

**Deliverable:** MVP ready for users

---

## MVP Timeline Summary

| Phase | Weeks | Duration | Key Deliverable |
|-------|-------|----------|-----------------|
| **Phase 0: Setup** | 1 | 1 week | Running server |
| **Phase 1: Foundation** | 2-3 | 2 weeks | Auth + Libraries |
| **Phase 2: Core** | 4-7 | 4 weeks | Book upload + management |
| **Phase 3: Reader** | 8-11 | 4 weeks | PDF reading |
| **Phase 4: Launch** | 12-16 | 5 weeks | MVP release |
| **Total** | | **16 weeks** | **Month 4 launch** |

---

## Post-MVP Roadmap

### Phase 5: Enhanced (Month 5-6)

**Goal:** Make it actually useful

**Features:**
- EPUB support
- Metadata fetching
- Book covers
- Search & filter
- Basic bookmarks

**Timeline:** 8-10 weeks

---

### Phase 6: Complete (Month 7-10)

**Goal:** Feature parity with current BookTower

**Features:**
- CBX reader
- Audiobook player
- Annotations
- Library scanning
- OPDS
- Statistics

**Timeline:** 12-16 weeks

---

### Phase 7: Polish (Month 11-12)

**Goal:** Production-ready

**Features:**
- Kobo sync
- KOReader sync
- Advanced settings
- Performance optimization
- Comprehensive testing

**Timeline:** 8-12 weeks

---

## Success Metrics

### MVP Success (Month 4)

**Technical:**
- ✅ Server stable (99% uptime)
- ✅ < 2 second page load
- ✅ Can handle 100 concurrent users
- ✅ Zero critical bugs

**User:**
- ✅ 10+ beta users actively using
- ✅ Users uploading books daily
- ✅ Users reading books
- ✅ Positive feedback

### Full Launch Success (Month 12)

**Technical:**
- ✅ Feature parity with current BookTower
- ✅ Better performance
- ✅ Lower resource usage
- ✅ Zero data loss

**User:**
- ✅ 100+ active users
- ✅ Users switching from old version
- ✅ Feature requests (good sign!)
- ✅ Community engagement

---

## Risk Mitigation

### Risk: MVP is too minimal

**Mitigation:**
- Focus on PDF (most popular format)
- Ensure PDF reader is excellent
- Add EPUB in Phase 2 (Month 5-6)

### Risk: Users won't switch

**Mitigation:**
- Run both versions in parallel
- Allow data export/import
- Gradual migration
- Feature parity before forcing switch

### Risk: Timeline blowout

**Mitigation:**
- Cut scope, not timeline
- Launch with fewer features if needed
- Defer non-critical features
- Weekly check-ins

### Risk: Technical issues

**Mitigation:**
- MVP stays simple
- Proven technologies only
- Comprehensive testing
- Easy rollback plan

---

## Resource Requirements

### Team (MVP)

**Ideal:**
- 1 Backend developer (Kotlin)
- 1 Frontend developer (HTML/HTMX)
- 1 DevOps (part-time)

**Minimum:**
- 1 Full-stack developer

### Time (MVP)

**Development:** 16 weeks (4 months)
**Buffer:** 4 weeks (testing, fixes)
**Total:** 20 weeks (5 months)

### Infrastructure (MVP)

**Development:**
- Local development environment
- Docker for database

**Production:**
- VPS (DigitalOcean, Linode, etc.)
- $20-40/month
- 2GB RAM, 2 CPU
- 50GB storage

---

## Conclusion

**MVP-First Approach = Smart**

**Why this works:**
1. **Ship in 4 months** vs 12+ months
2. **User feedback early** vs building in vacuum
3. **Iterate** vs big bang
4. **Lower risk** vs everything at once
5. **Maintain momentum** vs losing interest

**What you get:**
- Month 4: Working PDF library (simple but functional)
- Month 6: EPUB + metadata (more useful)
- Month 10: Feature-complete (full BookTower)
- Month 12: Polished (production-ready)

**Key to success:**
- **Ruthless scope control** - Stick to MVP features
- **Ship early** - Don't wait for perfection
- **Listen to users** - Let them guide priorities
- **Iterate fast** - Weekly releases

This is how successful rewrites happen. Not by rebuilding everything at once, but by building the core, shipping it, and iterating.

**Ready to start?** Let's build BookTower v2, MVP first! 🚀
