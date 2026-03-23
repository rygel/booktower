package org.booktower.handlers

import org.booktower.config.TemplateRenderer
import org.booktower.models.CreateMagicShelfRequest
import org.booktower.models.ShelfRuleType
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.MagicShelfService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form

class BrowsePageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val bookService: BookService,
    private val magicShelfService: MagicShelfService,
    private val templateRenderer: TemplateRenderer,
) {
    private fun auth(req: Request) = pageAuth(req, jwtService, authService)

    private fun pc(req: Request) = pageContext(req, jwtService, authService)

    /** GET /shelves/{id} */
    fun magicShelf(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val shelfId = req.lastPathSegment().toUuidOrNull() ?: return Response(Status.NOT_FOUND)
        val shelf = magicShelfService.getShelf(userId, shelfId) ?: return Response(Status.NOT_FOUND)
        val books = magicShelfService.resolveBooks(userId, shelf)
        return htmlOk(templateRenderer.render("shelf.kte", pc.toMap("shelf" to shelf, "books" to books)))
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
            } catch (_: IllegalArgumentException) {
                return Response(Status.BAD_REQUEST).body("Invalid ruleType")
            }
        val ruleValue: String? =
            when (ruleType) {
                ShelfRuleType.STATUS ->
                    req.form("ruleValueStatus")?.takeIf { it.isNotBlank() }
                        ?: return Response(Status.BAD_REQUEST).body("Status is required")
                ShelfRuleType.TAG ->
                    req.form("ruleValueTag")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                        ?: return Response(Status.BAD_REQUEST).body("Tag is required")
                ShelfRuleType.RATING_GTE ->
                    req.form("ruleValueRating")?.toIntOrNull()?.coerceIn(1, 5)?.toString()
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
        return Response(Status.OK).header("HX-Trigger", toast(ctx.i18n.translate("msg.shelf-deleted")))
    }

    /** GET /authors */
    fun authorList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val authors = bookService.getAuthors(userId)
        return htmlOk(templateRenderer.render("author-list.kte", pc.toMap("authors" to authors)))
    }

    /** GET /authors/{name} */
    fun author(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val name = req.lastPathSegment()?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksByAuthor(userId, name)
        return htmlOk(templateRenderer.render("author.kte", pc.toMap("authorName" to name, "books" to books)))
    }

    /** GET /series */
    fun seriesList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val series = bookService.getSeries(userId)
        return htmlOk(templateRenderer.render("series-list.kte", pc.toMap("series" to series)))
    }

    /** GET /series/{name} */
    fun series(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val name = req.lastPathSegment()?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksBySeries(userId, name)
        return htmlOk(templateRenderer.render("series.kte", pc.toMap("seriesName" to name, "books" to books)))
    }

    /** GET /tags */
    fun tagList(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val tags = bookService.getTagsWithCounts(userId)
        return htmlOk(templateRenderer.render("tag-list.kte", pc.toMap("tags" to tags)))
    }

    /** GET /tags/{name} */
    fun tag(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val name = req.lastPathSegment()?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooksByTag(userId, name)
        return htmlOk(templateRenderer.render("tag.kte", pc.toMap("tagName" to name, "books" to books)))
    }
}
