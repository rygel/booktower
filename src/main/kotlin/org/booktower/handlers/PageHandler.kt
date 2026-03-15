package org.booktower.handlers

import org.booktower.config.TemplateRenderer
import org.booktower.model.ThemeCatalog
import org.booktower.models.BookSortOrder
import org.booktower.models.ReadStatus
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateBookmarkRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.UpdateBookRequest
import org.booktower.models.UpdateLibraryRequest
import org.booktower.models.UpdateProgressRequest
import org.booktower.config.Json
import org.booktower.models.CreateMagicShelfRequest
import org.booktower.models.ShelfRuleType
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import java.util.UUID

class PageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val bookmarkService: BookmarkService,
    private val userSettingsService: UserSettingsService,
    private val analyticsService: AnalyticsService,
    private val annotationService: AnnotationService,
    private val metadataFetchService: MetadataFetchService,
    private val magicShelfService: MagicShelfService,
    private val templateRenderer: TemplateRenderer,
) {
    // ── Page routes ────────────────────────────────────────────────────────────

    fun libraries(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val libraries = libraryService.getLibraries(userId)
        val shelves = magicShelfService.getShelves(userId)
        return htmlOk(templateRenderer.render("libraries.kte", mapOf(
            "username" to null,
            "libraries" to libraries,
            "shelves" to shelves,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    /** GET /shelves/{id} */
    fun magicShelf(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val shelfId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val shelf = magicShelfService.getShelf(userId, shelfId) ?: return Response(Status.NOT_FOUND)
        val books = magicShelfService.resolveBooks(userId, shelf)
        return htmlOk(templateRenderer.render("shelf.kte", mapOf(
            "username" to null,
            "shelf" to shelf,
            "books" to books,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    /** POST /ui/shelves */
    fun createMagicShelf(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val name = req.form("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("Name is required")
        if (name.length > 100) return Response(Status.BAD_REQUEST).body("Name must be 100 characters or fewer")
        val ruleTypeStr = req.form("ruleType")?.trim()
            ?: return Response(Status.BAD_REQUEST).body("ruleType is required")
        val ruleType = try { ShelfRuleType.valueOf(ruleTypeStr) }
            catch (_: IllegalArgumentException) { return Response(Status.BAD_REQUEST).body("Invalid ruleType") }
        val ruleValue: String? = when (ruleType) {
            ShelfRuleType.STATUS -> req.form("ruleValueStatus")?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Status is required")
            ShelfRuleType.TAG -> req.form("ruleValueTag")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Tag is required")
            ShelfRuleType.RATING_GTE -> req.form("ruleValueRating")?.toIntOrNull()
                ?.coerceIn(1, 5)?.toString()
                ?: return Response(Status.BAD_REQUEST).body("Rating is required")
        }
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest(name, ruleType, ruleValue))
        val rendered = templateRenderer.render("components/shelfCard.kte", mapOf("shelf" to shelf, "i18n" to ctx.i18n))
        return Response(Status.CREATED)
            .header("Content-Type", "text/html; charset=utf-8")
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.shelf-created")))
            .body(rendered)
    }

    /** DELETE /ui/shelves/{id} */
    fun deleteMagicShelf(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val shelfId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        magicShelfService.deleteShelf(userId, shelfId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.shelf-deleted")))
    }

    fun library(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val library = libraryService.getLibrary(userId, libId) ?: return Response(Status.NOT_FOUND)
        val sortParam = req.query("sort")
        val sortBy = if (sortParam != null) {
            val explicit = BookSortOrder.entries.firstOrNull { it.name.equals(sortParam, ignoreCase = true) }
            if (explicit != null) {
                userSettingsService.set(userId, "book.sort", explicit.name)
                explicit
            } else BookSortOrder.TITLE
        } else {
            val saved = userSettingsService.get(userId, "book.sort")
            BookSortOrder.entries.firstOrNull { it.name == saved } ?: BookSortOrder.TITLE
        }
        val statusParam = req.query("status")
        val statusFilter = statusParam?.let { s ->
            ReadStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
        }
        val tagParam = req.query("tag")
        val tagFilter = tagParam?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ratingFilter = req.query("rating")?.toIntOrNull()?.coerceIn(1, 5)
        val userTags = bookService.getUserTags(userId)
        val books = bookService.getBooks(userId, libId.toString(), 1, 200, sortBy, statusFilter?.name, tagFilter, ratingFilter).getBooks()
        return htmlOk(templateRenderer.render("library.kte", mapOf(
            "username" to null,
            "library" to library,
            "books" to books,
            "currentSort" to sortBy.name,
            "sortOptions" to BookSortOrder.entries.toList(),
            "currentStatus" to (statusFilter?.name ?: "ALL"),
            "statusOptions" to ReadStatus.entries.toList(),
            "currentTag" to (tagFilter ?: ""),
            "userTags" to userTags,
            "currentRating" to (ratingFilter ?: 0),
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    fun book(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val bookId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        val libraries = libraryService.getLibraries(userId)
        val libraryName = libraries.firstOrNull { it.id == book.libraryId }?.name
        return htmlOk(templateRenderer.render("book.kte", mapOf(
            "username" to null,
            "book" to book,
            "libraryName" to libraryName,
            "libraries" to libraries,
            "bookmarks" to bookmarks,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    fun reader(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        val filePath = bookService.getBookFilePath(userId, bookId)
        val readerType = when {
            book.fileSize <= 0 || filePath.isNullOrBlank() -> "none"
            else -> when (filePath.substringAfterLast('.', "").lowercase()) {
                "epub" -> "epub"
                "cbz", "cbr" -> "comic"
                else -> "pdf"
            }
        }
        return htmlOk(templateRenderer.render("reader.kte", mapOf(
            "book" to book,
            "bookmarks" to bookmarks,
            "readerType" to readerType,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "i18n" to ctx.i18n,
        )))
    }

    /** GET /ui/books/{id}/annotations */
    fun getAnnotations(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val page = req.query("page")?.toIntOrNull()
        val annotations = annotationService.getAnnotations(userId, bookId, page)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(annotations))
    }

    /** POST /ui/books/{id}/annotations */
    fun createAnnotation(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val page = req.form("page")?.toIntOrNull() ?: return Response(Status.BAD_REQUEST).body("page required")
        val selectedText = req.form("selectedText")?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("selectedText required")
        val color = req.form("color")?.takeIf { it.isNotBlank() } ?: "yellow"
        val annotation = annotationService.createAnnotation(userId, bookId, page, selectedText, color)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(annotation))
    }

    /** DELETE /ui/annotations/{id} */
    fun deleteAnnotation(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val annotationId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val deleted = annotationService.deleteAnnotation(userId, annotationId)
        return if (deleted) Response(Status.OK) else Response(Status.NOT_FOUND)
    }

    /** POST /ui/books/{id}/fetch-metadata */
    fun fetchMetadata(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)

        val meta = metadataFetchService.fetchMetadata(book.title, book.author)
            ?: return Response(Status.OK)
                .header("HX-Trigger", toast(ctx.i18n.translate("msg.metadata.not.found"), "info"))

        bookService.applyFetchedMetadata(userId, bookId, meta)

        return Response(Status.OK)
            .header("HX-Redirect", "/books/$bookId")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.metadata.fetched"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    fun search(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val query = req.query("q") ?: ""
        val page = req.query("page")?.toIntOrNull() ?: 1
        val result = if (query.isNotBlank()) bookService.searchBooks(userId, query, page, 40) else null
        return htmlOk(templateRenderer.render("search.kte", mapOf(
            "username" to null,
            "query" to query,
            "books" to (result?.getBooks() ?: emptyList<Any>()),
            "total" to (result?.total ?: 0),
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    fun dashboard(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val libraries = libraryService.getLibraries(userId)
        val recentBooks = bookService.getRecentBooks(userId, 6)
        val recentlyAddedBooks = bookService.getRecentlyAddedBooks(userId, 6)
        val totalBooks = libraries.sumOf { it.bookCount }
        val year = java.time.LocalDate.now().year
        val goal = userSettingsService.get(userId, "reading.goal.$year")?.toIntOrNull() ?: 0
        val booksFinishedThisYear = bookService.countFinishedThisYear(userId, year)
        return htmlOk(templateRenderer.render("dashboard.kte", mapOf(
            "username" to null,
            "libraries" to libraries,
            "recentBooks" to recentBooks,
            "recentlyAddedBooks" to recentlyAddedBooks,
            "libraryCount" to libraries.size,
            "totalBooks" to totalBooks,
            "goal" to goal,
            "booksFinishedThisYear" to booksFinishedThisYear,
            "year" to year,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    /** POST /ui/goal — save yearly reading goal */
    fun setGoal(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val year = java.time.LocalDate.now().year
        val goal = req.form("goal")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        userSettingsService.set(userId, "reading.goal.$year", goal.toString())
        return Response(Status.OK)
    }

    fun profile(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val user = authService.getUserById(userId)
        val analyticsEnabled = userSettingsService.get(userId, "analytics.enabled") == "true"
        return htmlOk(templateRenderer.render("profile.kte", mapOf(
            "username" to null,
            "userEmail" to (user?.email ?: ""),
            "userUsername" to (user?.username ?: ""),
            "memberSince" to (user?.createdAt?.toString()?.take(10) ?: ""),
            "analyticsEnabled" to analyticsEnabled,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    fun analytics(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val summary = analyticsService.getSummary(userId)
        return htmlOk(templateRenderer.render("analytics.kte", mapOf(
            "username" to null,
            "summary" to summary,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
            "isAdmin" to authIsAdmin(req),
        )))
    }

    fun setAnalytics(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val enabled = req.form("enabled")?.lowercase() == "true"
        userSettingsService.set(userId, "analytics.enabled", if (enabled) "true" else "false")
        return Response(Status.OK)
    }

    /** POST /ui/books/{id}/rating */
    fun setRating(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val rating = req.form("rating")?.toIntOrNull()
        bookService.setRating(userId, bookId, rating)
        return Response(Status.OK)
    }

    /** POST /ui/books/{id}/tags */
    fun setTags(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val tagsRaw = req.form("tags") ?: ""
        val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        bookService.setTags(userId, bookId, tags)
        return Response(Status.OK)
    }

    /** POST /ui/books/{id}/status */
    fun setStatus(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val statusName = req.form("status")?.takeIf { it.isNotBlank() && it != "NONE" }
        val status = statusName?.let { n -> ReadStatus.entries.firstOrNull { it.name == n } }
        bookService.setStatus(userId, bookId, status)
        return Response(Status.OK)
    }

    // ── HTMX mutation endpoints ────────────────────────────────────────────────

    fun createLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val name = req.form("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("Name is required")
        val storagePath = req.form("path")?.trim()?.takeIf { it.isNotBlank() }
            ?: "./data/libraries/${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        val library = libraryService.createLibrary(userId, CreateLibraryRequest(name, storagePath))
        return htmlOk(templateRenderer.render("components/libraryCard.kte", mapOf(
            "lib" to library,
            "i18n" to ctx.i18n,
        ))).header("HX-Trigger", toast(ctx.i18n.translate("msg.library-created")))
    }

    fun deleteLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        libraryService.deleteLibrary(userId, libId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.library-deleted")))
    }

    /** POST /ui/libraries/{id}/rename */
    fun renameLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val name = req.form("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("Name is required")
        libraryService.renameLibrary(userId, libId, UpdateLibraryRequest(name))
            ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("HX-Redirect", "/libraries/${libId}")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.library-renamed"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    /** POST /ui/libraries/{libId}/books — path is .../SOME-UUID/books */
    fun createBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId = req.secondToLastPathSegment()
            ?: return Response(Status.BAD_REQUEST)
        val title = req.form("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("Title is required")
        val author = req.form("author")?.trim()?.takeIf { it.isNotBlank() }
        val result = bookService.createBook(userId, CreateBookRequest(title, author, null, libId))
        return result.fold(
            onSuccess = { b ->
                htmlOk(templateRenderer.render("components/bookCard.kte", mapOf(
                    "book" to b,
                    "i18n" to ctx.i18n,
                ))).header("HX-Trigger", toast(ctx.i18n.translate("msg.book-added")))
            },
            onFailure = { Response(Status.BAD_REQUEST).body(it.message ?: "Error creating book") },
        )
    }

    /** POST /ui/books/{id}/move */
    fun moveBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val targetLibraryId = req.form("targetLibraryId")?.let { id ->
            try { UUID.fromString(id) } catch (_: IllegalArgumentException) { null }
        } ?: return Response(Status.BAD_REQUEST).body("targetLibraryId required")
        val moved = bookService.moveBook(userId, bookId, targetLibraryId)
            ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("HX-Redirect", "/libraries/${moved.libraryId}")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.book-moved"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    fun deleteBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        bookService.deleteBook(userId, bookId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.book-deleted")))
    }

    /** POST /ui/books/{id}/meta */
    fun editBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val title = req.form("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: return Response(Status.BAD_REQUEST).body("Title is required")
        if (title.length > 255) return Response(Status.BAD_REQUEST).body("Title must be 255 characters or fewer")
        val author = req.form("author")?.trim()?.takeIf { it.isNotBlank() }
        val description = req.form("description")?.trim()?.takeIf { it.isNotBlank() }
        bookService.updateBook(userId, bookId, UpdateBookRequest(title, author, description))
            ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("HX-Redirect", "/books/${bookId}")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.book-updated"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    /** POST /ui/books/{id}/progress — path ends in .../UUID/progress */
    fun updateProgress(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val currentPage = req.form("currentPage")?.toIntOrNull()
            ?: return Response(Status.BAD_REQUEST).body("currentPage required")
        bookService.updateProgress(userId, bookId, UpdateProgressRequest(currentPage))
        return Response(Status.OK)
    }

    /** POST /ui/books/{id}/bookmarks — path ends in .../UUID/bookmarks */
    fun createBookmark(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment()
            ?: return Response(Status.BAD_REQUEST)
        val page = req.form("page")?.toIntOrNull()
            ?: return Response(Status.BAD_REQUEST).body("page required")
        val title = req.form("title")?.trim()?.takeIf { it.isNotBlank() }
        val note = req.form("note")?.trim()?.takeIf { it.isNotBlank() }
        val result = bookmarkService.createBookmark(userId, CreateBookmarkRequest(bookId, page, title, note))
        return result.fold(
            onSuccess = { bm ->
                htmlOk(templateRenderer.render("components/bookmarkItem.kte", mapOf("bookmark" to bm)))
                    .header("HX-Trigger", toast(ctx.i18n.translate("msg.bookmark-saved")))
            },
            onFailure = { Response(Status.BAD_REQUEST).body(it.message ?: "Error") },
        )
    }

    fun deleteBookmark(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookmarkId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        bookmarkService.deleteBookmark(userId, bookmarkId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.bookmark-deleted")))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun auth(req: Request): UUID? {
        val token = req.cookie("token")?.value ?: return null
        return jwtService.extractUserId(token)
    }

    private fun authIsAdmin(req: Request): Boolean {
        val token = req.cookie("token")?.value ?: return false
        return jwtService.extractIsAdmin(token)
    }

    private fun redirectToLogin() = Response(Status.SEE_OTHER).header("Location", "/login")

    private fun htmlOk(html: String) =
        Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(html)

    private fun Request.lastPathSegment(): String? =
        uri.path.split("/").filter { it.isNotBlank() }.lastOrNull()

    private fun Request.secondToLastPathSegment(): String? {
        val parts = uri.path.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) parts[parts.size - 2] else null
    }

    private fun String?.toUuidOrNull(): UUID? =
        if (this == null) null else try { UUID.fromString(this) } catch (_: IllegalArgumentException) { null }

    private fun toast(message: String, type: String = "success"): String =
        """{"showToast":{"message":"${message.replace("\"", "\\\"")}","type":"$type"}}"""
}
