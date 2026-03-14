package org.booktower.handlers

import org.booktower.config.TemplateRenderer
import org.booktower.model.ThemeCatalog
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateBookmarkRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.UpdateProgressRequest
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.cookie
import java.util.UUID

class PageHandler(
    private val jwtService: JwtService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val bookmarkService: BookmarkService,
    private val templateRenderer: TemplateRenderer,
) {
    // ── Page routes ────────────────────────────────────────────────────────────

    fun libraries(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val libraries = libraryService.getLibraries(userId)
        return htmlOk(templateRenderer.render("libraries.kte", mapOf(
            "username" to null,
            "libraries" to libraries,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
        )))
    }

    fun library(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val library = libraryService.getLibrary(userId, libId) ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooks(userId, libId.toString(), 1, 200).getBooks()
        return htmlOk(templateRenderer.render("library.kte", mapOf(
            "username" to null,
            "library" to library,
            "books" to books,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
        )))
    }

    fun book(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val bookId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        return htmlOk(templateRenderer.render("book.kte", mapOf(
            "username" to null,
            "book" to book,
            "bookmarks" to bookmarks,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "themes" to ThemeCatalog.allThemes(),
            "i18n" to ctx.i18n,
        )))
    }

    fun reader(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val ctx = WebContext(req)
        val bookId = req.secondToLastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        return htmlOk(templateRenderer.render("reader.kte", mapOf(
            "book" to book,
            "bookmarks" to bookmarks,
            "themeCss" to ctx.themeCss,
            "currentTheme" to ctx.theme,
            "lang" to ctx.lang,
            "i18n" to ctx.i18n,
        )))
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
        )))
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
        )))
    }

    fun deleteLibrary(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val libId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        libraryService.deleteLibrary(userId, libId)
        return Response(Status.OK)
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
                )))
            },
            onFailure = { Response(Status.BAD_REQUEST).body(it.message ?: "Error creating book") },
        )
    }

    fun deleteBook(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        bookService.deleteBook(userId, bookId)
        return Response(Status.OK)
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
            },
            onFailure = { Response(Status.BAD_REQUEST).body(it.message ?: "Error") },
        )
    }

    fun deleteBookmark(req: Request): Response {
        val userId = auth(req) ?: return Response(Status.UNAUTHORIZED)
        val bookmarkId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.BAD_REQUEST)
        bookmarkService.deleteBookmark(userId, bookmarkId)
        return Response(Status.OK)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun auth(req: Request): UUID? {
        val token = req.cookie("token")?.value ?: return null
        return jwtService.extractUserId(token)
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
}
