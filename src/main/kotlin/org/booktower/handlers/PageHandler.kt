package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.model.ThemeCatalog
import org.booktower.models.BookSortOrder
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateBookmarkRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateMagicShelfRequest
import org.booktower.models.ReadStatus
import org.booktower.models.ShelfRuleType
import org.booktower.models.UpdateBookRequest
import org.booktower.models.UpdateLibraryRequest
import org.booktower.models.UpdateProgressRequest
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.ReadingSessionService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

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
    private val readingSessionService: ReadingSessionService? = null,
    private val libraryWatchService: org.booktower.services.LibraryWatchService? = null,
    private val bookLinkService: org.booktower.services.BookLinkService? = null,
) {
    // ── Page routes ────────────────────────────────────────────────────────────

    fun libraries(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val libraries = libraryService.getLibraries(userId)
        val shelves = magicShelfService.getShelves(userId)
        return htmlOk(
            templateRenderer.render(
                "libraries.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "libraries" to libraries,
                    "shelves" to shelves,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /shelves/{id} */
    fun magicShelf(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val shelfId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val shelf = magicShelfService.getShelf(userId, shelfId) ?: return Response(Status.NOT_FOUND)
        val books = magicShelfService.resolveBooks(userId, shelf)
        return htmlOk(
            templateRenderer.render(
                "shelf.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "shelf" to shelf,
                    "books" to books,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** POST /ui/shelves */
    fun createMagicShelf(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val name =
            req.form("name")?.trim()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Name is required")
        if (name.length > 100) return Response(Status.BAD_REQUEST).body("Name must be 100 characters or fewer")
        val ruleTypeStr =
            req.form("ruleType")?.trim()
                ?: return Response(Status.BAD_REQUEST).body("ruleType is required")
        val ruleType =
            try {
                ShelfRuleType.valueOf(ruleTypeStr)
            } catch (
                _: IllegalArgumentException,
            ) {
                return Response(Status.BAD_REQUEST).body("Invalid ruleType")
            }
        val ruleValue: String? =
            when (ruleType) {
                ShelfRuleType.STATUS -> {
                    req.form("ruleValueStatus")?.takeIf { it.isNotBlank() }
                        ?: return Response(Status.BAD_REQUEST).body("Status is required")
                }

                ShelfRuleType.TAG -> {
                    req
                        .form("ruleValueTag")
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotBlank() }
                        ?: return Response(Status.BAD_REQUEST).body("Tag is required")
                }

                ShelfRuleType.RATING_GTE -> {
                    req
                        .form("ruleValueRating")
                        ?.toIntOrNull()
                        ?.coerceIn(1, 5)
                        ?.toString()
                        ?: return Response(Status.BAD_REQUEST).body("Rating is required")
                }
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
        val (username, gravatarHash) = userDisplayInfo(userId)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val library = libraryService.getLibrary(userId, libId) ?: return Response(Status.NOT_FOUND)
        val sortParam = req.query("sort")
        val sortBy =
            if (sortParam != null) {
                val explicit = BookSortOrder.entries.firstOrNull { it.name.equals(sortParam, ignoreCase = true) }
                if (explicit != null) {
                    userSettingsService.set(userId, "book.sort", explicit.name)
                    explicit
                } else {
                    BookSortOrder.TITLE
                }
            } else {
                val saved = userSettingsService.get(userId, "book.sort")
                BookSortOrder.entries.firstOrNull { it.name == saved } ?: BookSortOrder.TITLE
            }
        val statusParam = req.query("status")
        val statusFilter =
            statusParam?.let { s ->
                ReadStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
            }
        val tagParam = req.query("tag")
        val tagFilter = tagParam?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ratingFilter = req.query("rating")?.toIntOrNull()?.coerceIn(1, 5)
        val currentPage = req.query("page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pageSize = 50
        val userTags = bookService.getUserTags(userId)
        val result =
            bookService.getBooks(
                userId,
                libId.toString(),
                currentPage,
                pageSize,
                sortBy,
                statusFilter?.name,
                tagFilter,
                ratingFilter,
            )
        val totalPages = if (result.total == 0) 1 else (result.total + pageSize - 1) / pageSize
        return htmlOk(
            templateRenderer.render(
                "library.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "library" to library,
                    "books" to result.getBooks(),
                    "currentSort" to sortBy.name,
                    "sortOptions" to BookSortOrder.entries.toList(),
                    "currentStatus" to (statusFilter?.name ?: "ALL"),
                    "statusOptions" to ReadStatus.entries.toList(),
                    "currentTag" to (tagFilter ?: ""),
                    "userTags" to userTags,
                    "currentRating" to (ratingFilter ?: 0),
                    "currentPage" to currentPage,
                    "totalPages" to totalPages,
                    "totalBooks" to result.total,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    fun book(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val bookId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        val libraries = libraryService.getLibraries(userId)
        val libraryName = libraries.firstOrNull { it.id == book.libraryId }?.name
        val chapters = bookService.getBookFiles(userId, bookId)
        val linkedBook = bookLinkService?.getLinkForBook(userId, bookId)
        val syncPosition = if (linkedBook != null) bookLinkService?.syncPosition(userId, bookId) else null
        return htmlOk(
            templateRenderer.render(
                "book.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "book" to book,
                    "libraryName" to libraryName,
                    "libraries" to libraries,
                    "bookmarks" to bookmarks,
                    "chapters" to chapters,
                    "linkedBook" to linkedBook,
                    "syncPosition" to syncPosition,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    fun reader(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        val filePath = bookService.getBookFilePath(userId, bookId)
        val hasChapters = bookService.hasBookFiles(userId, bookId)
        val readerType =
            when {
                hasChapters -> {
                    "audio-multi"
                }

                book.fileSize <= 0 || filePath.isNullOrBlank() -> {
                    "none"
                }

                else -> {
                    when (filePath.substringAfterLast('.', "").lowercase()) {
                        "epub" -> "epub"
                        "cbz", "cbr" -> "comic"
                        "mp3", "m4b", "m4a", "ogg", "flac", "aac" -> "audio"
                        "fb2" -> "fb2"
                        "mobi", "azw3" -> "kindle"
                        else -> "pdf"
                    }
                }
            }
        val chapters = bookService.getBookFiles(userId, bookId)
        return htmlOk(
            templateRenderer.render(
                "reader.kte",
                mapOf(
                    "book" to book,
                    "bookmarks" to bookmarks,
                    "readerType" to readerType,
                    "chapters" to chapters,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            ),
        )
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
        val selectedText =
            req.form("selectedText")?.takeIf { it.isNotBlank() }
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

        val source = req.query("source")?.takeIf { it.isNotBlank() }
        val meta =
            metadataFetchService.fetchMetadata(book.title, book.author, source)
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
        val (username, gravatarHash) = userDisplayInfo(userId)
        val query = req.query("q") ?: ""
        val page = req.query("page")?.toIntOrNull() ?: 1
        val libId = req.query("libId")?.takeIf { it.isNotBlank() }
        val statusFilter = req.query("status")?.takeIf { it.isNotBlank() && it != "ALL" }
        val minRating = req.query("rating")?.toIntOrNull()?.coerceIn(1, 5)
        val result =
            if (query.isNotBlank()) {
                bookService.searchBooks(userId, query, page, 40, libId, statusFilter, minRating)
            } else {
                null
            }
        return htmlOk(
            templateRenderer.render(
                "search.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "query" to query,
                    "books" to (result?.getBooks() ?: emptyList<Any>()),
                    "total" to (result?.total ?: 0),
                    "currentStatus" to (statusFilter ?: "ALL"),
                    "currentRating" to (minRating ?: 0),
                    "statusOptions" to
                        org.booktower.models.ReadStatus.entries
                            .toList(),
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    fun dashboard(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val year =
            java.time.LocalDate
                .now()
                .year

        // Run all independent queries in parallel using virtual threads
        val vt = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val fLibraries = CompletableFuture.supplyAsync({ libraryService.getLibraries(userId) }, vt)
            val fRecent = CompletableFuture.supplyAsync({ bookService.getRecentBooks(userId, 6) }, vt)
            val fRecentlyAdded = CompletableFuture.supplyAsync({ bookService.getRecentlyAddedBooks(userId, 6) }, vt)
            val fRecentlyFinished = CompletableFuture.supplyAsync({ bookService.getRecentlyFinishedBooks(userId, 6) }, vt)
            val fReadingCount = CompletableFuture.supplyAsync({ bookService.countByStatus(userId, org.booktower.models.ReadStatus.READING) }, vt)
            val fGoal = CompletableFuture.supplyAsync({ userSettingsService.get(userId, "reading.goal.$year")?.toIntOrNull() ?: 0 }, vt)
            val fFinishedThisYear = CompletableFuture.supplyAsync({ bookService.countFinishedThisYear(userId, year) }, vt)

            CompletableFuture.allOf(fLibraries, fRecent, fRecentlyAdded, fRecentlyFinished, fReadingCount, fGoal, fFinishedThisYear).join()

            val libraries = fLibraries.get()
            val recentBooks = fRecent.get()
            val recentlyAddedBooks = fRecentlyAdded.get()
            val recentlyFinishedBooks = fRecentlyFinished.get()
            val currentlyReadingCount = fReadingCount.get()
            val goal = fGoal.get()
            val booksFinishedThisYear = fFinishedThisYear.get()
            val totalBooks = libraries.sumOf { it.bookCount }

            return htmlOk(
                templateRenderer.render(
                    "dashboard.kte",
                    mapOf(
                        "username" to username,
                        "gravatarHash" to gravatarHash,
                        "libraries" to libraries,
                        "recentBooks" to recentBooks,
                        "recentlyAddedBooks" to recentlyAddedBooks,
                        "recentlyFinishedBooks" to recentlyFinishedBooks,
                        "libraryCount" to libraries.size,
                        "totalBooks" to totalBooks,
                        "currentlyReadingCount" to currentlyReadingCount,
                        "goal" to goal,
                        "booksFinishedThisYear" to booksFinishedThisYear,
                        "year" to year,
                        "themeCss" to ctx.themeCss,
                        "currentTheme" to ctx.theme,
                        "lang" to ctx.lang,
                        "themes" to ThemeCatalog.allThemes(),
                        "i18n" to ctx.i18n,
                        "isAdmin" to authIsAdmin(req),
                    ),
                ),
            )
        } finally {
            vt.close()
        }
    }

    /** POST /ui/goal — save yearly reading goal */
    fun setGoal(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val year =
            java.time.LocalDate
                .now()
                .year
        val goal = req.form("goal")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        userSettingsService.set(userId, "reading.goal.$year", goal.toString())
        return Response(Status.OK)
    }

    fun profile(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val user = authService.getUserById(userId)
        val analyticsEnabled = userSettingsService.get(userId, "analytics.enabled") == "true"
        val libraries = libraryService.getLibraries(userId)
        return htmlOk(
            templateRenderer.render(
                "profile.kte",
                mapOf(
                    "username" to (user?.username),
                    "gravatarHash" to (user?.email?.let { gravatarHash(it) } ?: ""),
                    "userEmail" to (user?.email ?: ""),
                    "userUsername" to (user?.username ?: ""),
                    "memberSince" to (user?.createdAt?.toString()?.take(10) ?: ""),
                    "analyticsEnabled" to analyticsEnabled,
                    "libraries" to libraries,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    fun activity(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        return htmlOk(
            templateRenderer.render(
                "activity.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    fun analytics(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val summary = analyticsService.getSummary(userId)
        val recentSessions = readingSessionService?.getRecentSessions(userId, 20) ?: emptyList()
        return htmlOk(
            templateRenderer.render(
                "analytics.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "summary" to summary,
                    "recentSessions" to recentSessions,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
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
        val name =
            req.form("name")?.trim()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Name is required")
        val storagePath =
            req.form("path")?.trim()?.takeIf { it.isNotBlank() }
                ?: "./data/libraries/${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        val library = libraryService.createLibrary(userId, CreateLibraryRequest(name, storagePath))
        libraryWatchService?.registerLibrary(userId, java.util.UUID.fromString(library.id), library.path)
        return htmlOk(
            templateRenderer.render(
                "components/libraryCard.kte",
                mapOf(
                    "lib" to library,
                    "i18n" to ctx.i18n,
                ),
            ),
        ).header("HX-Trigger", toast(ctx.i18n.translate("msg.library-created")))
    }

    fun deleteLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        libraryService.deleteLibrary(userId, libId)
        libraryWatchService?.unregisterLibrary(libId)
        return Response(Status.OK)
            .header("HX-Trigger", toast(ctx.i18n.translate("msg.library-deleted")))
    }

    /** POST /ui/libraries/{id}/rename */
    fun renameLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val name =
            req.form("name")?.trim()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Name is required")
        libraryService.renameLibrary(userId, libId, UpdateLibraryRequest(name))
            ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("HX-Redirect", "/libraries/$libId")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.library-renamed"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    /** POST /ui/libraries/{libId}/books — path is .../SOME-UUID/books */
    fun createBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val libId =
            req.secondToLastPathSegment()
                ?: return Response(Status.BAD_REQUEST)
        val title =
            req.form("title")?.trim()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Title is required")
        val author = req.form("author")?.trim()?.takeIf { it.isNotBlank() }
        val result = bookService.createBook(userId, CreateBookRequest(title, author, null, libId))
        return result.fold(
            onSuccess = { b ->
                htmlOk(
                    templateRenderer.render(
                        "components/bookCard.kte",
                        mapOf(
                            "book" to b,
                            "i18n" to ctx.i18n,
                        ),
                    ),
                ).header("HX-Trigger", toast(ctx.i18n.translate("msg.book-added")))
            },
            onFailure = { Response(Status.BAD_REQUEST).body(it.message ?: "Error creating book") },
        )
    }

    /** POST /ui/books/{id}/move */
    fun moveBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val targetLibraryId =
            req.form("targetLibraryId")?.let { id ->
                try {
                    UUID.fromString(id)
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: return Response(Status.BAD_REQUEST).body("targetLibraryId required")
        val moved =
            bookService.moveBook(userId, bookId, targetLibraryId)
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
        val title =
            req.form("title")?.trim()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("Title is required")
        if (title.length > 255) return Response(Status.BAD_REQUEST).body("Title must be 255 characters or fewer")
        val author = req.form("author")?.trim()?.takeIf { it.isNotBlank() }
        val description = req.form("description")?.trim()?.takeIf { it.isNotBlank() }
        val series = req.form("series")?.trim()?.takeIf { it.isNotBlank() }
        val seriesIndex = req.form("seriesIndex")?.toDoubleOrNull()
        val isbn = req.form("isbn")?.trim()?.takeIf { it.isNotBlank() }
        val publisher = req.form("publisher")?.trim()?.takeIf { it.isNotBlank() }
        val publishedDate = req.form("publishedDate")?.trim()?.takeIf { it.isNotBlank() }
        val pageCount = req.form("pageCount")?.toIntOrNull()?.coerceAtLeast(0)
        val subtitle = req.form("subtitle")?.trim()?.takeIf { it.isNotBlank() }
        val language = req.form("language")?.trim()?.takeIf { it.isNotBlank() }
        val contentRating = req.form("contentRating")?.trim()?.takeIf { it.isNotBlank() }
        val ageRating = req.form("ageRating")?.trim()?.takeIf { it.isNotBlank() }
        val goodreadsId = req.form("goodreadsId")?.trim()?.takeIf { it.isNotBlank() }
        val hardcoverId = req.form("hardcoverId")?.trim()?.takeIf { it.isNotBlank() }
        val comicvineId = req.form("comicvineId")?.trim()?.takeIf { it.isNotBlank() }
        val openlibraryId = req.form("openlibraryId")?.trim()?.takeIf { it.isNotBlank() }
        val googleBooksId = req.form("googleBooksId")?.trim()?.takeIf { it.isNotBlank() }
        val amazonId = req.form("amazonId")?.trim()?.takeIf { it.isNotBlank() }
        val audibleId = req.form("audibleId")?.trim()?.takeIf { it.isNotBlank() }
        bookService.updateBook(
            userId,
            bookId,
            UpdateBookRequest(
                title,
                author,
                description,
                series,
                seriesIndex,
                isbn,
                publisher,
                publishedDate,
                pageCount,
                subtitle,
                language,
                contentRating,
                ageRating,
                goodreadsId,
                hardcoverId,
                comicvineId,
                openlibraryId,
                googleBooksId,
                amazonId,
                audibleId,
            ),
        )
            ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("HX-Redirect", "/books/$bookId")
            .cookie(Cookie("flash_msg", ctx.i18n.translate("msg.book-updated"), path = "/"))
            .cookie(Cookie("flash_type", "success", path = "/"))
    }

    /** POST /ui/books/{id}/progress — path ends in .../UUID/progress */
    fun updateProgress(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        val currentPage =
            req.form("currentPage")?.toIntOrNull()
                ?: return Response(Status.BAD_REQUEST).body("currentPage required")
        bookService.updateProgress(userId, bookId, UpdateProgressRequest(currentPage))
        return Response(Status.OK)
    }

    /** POST /ui/books/{id}/bookmarks — path ends in .../UUID/bookmarks */
    fun createBookmark(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val ctx = WebContext(req)
        val bookId =
            req.secondToLastPathSegment()
                ?: return Response(Status.BAD_REQUEST)
        val page =
            req.form("page")?.toIntOrNull()
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

    /** GET /queue — Want to Read books across all libraries, sorted by date added */
    fun queue(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val page = req.query("page")?.toIntOrNull() ?: 1
        val result =
            bookService.getBooks(
                userId,
                null,
                page,
                40,
                org.booktower.models.BookSortOrder.ADDED,
                statusFilter = "WANT_TO_READ",
            )
        val totalPages = if (result.total == 0) 1 else (result.total + 39) / 40
        return htmlOk(
            templateRenderer.render(
                "queue.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "books" to result.getBooks(),
                    "total" to result.total,
                    "currentPage" to result.page,
                    "totalPages" to totalPages,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /authors — list all authors for the user */
    fun authorList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val authors = bookService.getAuthors(userId)
        return htmlOk(
            templateRenderer.render(
                "author-list.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "authors" to authors,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /authors/{name} — books by a specific author */
    fun author(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val name =
            req
                .lastPathSegment()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksByAuthor(userId, name)
        return htmlOk(
            templateRenderer.render(
                "author.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "authorName" to name,
                    "books" to books,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /series — list all series for the user */
    fun seriesList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val series = bookService.getSeries(userId)
        return htmlOk(
            templateRenderer.render(
                "series-list.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "series" to series,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /series/{name} — books in a specific series */
    fun series(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val name =
            req
                .lastPathSegment()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksBySeries(userId, name)
        return htmlOk(
            templateRenderer.render(
                "series.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "seriesName" to name,
                    "books" to books,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /tags — list all tags for the user */
    fun tagList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val tags = bookService.getTagsWithCounts(userId)
        return htmlOk(
            templateRenderer.render(
                "tag-list.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "tags" to tags,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    /** GET /tags/{name} — books with a specific tag */
    fun tag(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val (username, gravatarHash) = userDisplayInfo(userId)
        val name =
            req
                .lastPathSegment()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksByTag(userId, name)
        return htmlOk(
            templateRenderer.render(
                "tag.kte",
                mapOf(
                    "username" to username,
                    "gravatarHash" to gravatarHash,
                    "tagName" to name,
                    "books" to books,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "themes" to ThemeCatalog.allThemes(),
                    "i18n" to ctx.i18n,
                    "isAdmin" to authIsAdmin(req),
                ),
            ),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun auth(req: Request): UUID? {
        val token = req.cookie("token")?.value ?: return null
        val userId = jwtService.extractUserId(token) ?: return null
        return if (authService.getUserById(userId) != null) userId else null
    }

    /** Returns the username and Gravatar hash for the authenticated user. */
    private fun userDisplayInfo(userId: UUID): Pair<String?, String> {
        val user = authService.getUserById(userId)
        return Pair(user?.username, user?.email?.let { gravatarHash(it) } ?: "")
    }

    private fun gravatarHash(email: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md
            .digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun authIsAdmin(req: Request): Boolean {
        val token = req.cookie("token")?.value ?: return false
        return jwtService.extractIsAdmin(token)
    }

    private fun redirectToLogin() =
        Response(Status.SEE_OTHER)
            .header("Location", "/login")
            .cookie(Cookie(name = "token", value = "", path = "/", maxAge = 0))

    private fun htmlOk(html: String) = Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(html)

    private fun Request.lastPathSegment(): String? =
        uri.path
            .split("/")
            .filter { it.isNotBlank() }
            .lastOrNull()

    private fun Request.secondToLastPathSegment(): String? {
        val parts = uri.path.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) parts[parts.size - 2] else null
    }

    private fun String?.toUuidOrNull(): UUID? =
        if (this == null) {
            null
        } else {
            try {
                UUID.fromString(this)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun toast(
        message: String,
        type: String = "success",
    ): String = """{"showToast":{"message":"${message.replace("\"", "\\\"")}","type":"$type"}}"""
}
