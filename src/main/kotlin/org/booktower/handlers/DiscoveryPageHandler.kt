package org.booktower.handlers

import org.booktower.config.TemplateRenderer
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.Request
import org.http4k.core.Response

class DiscoveryPageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val templateRenderer: TemplateRenderer,
    private val discoveryService: org.booktower.services.DiscoveryService? = null,
    private val readingListService: org.booktower.services.ReadingListService? = null,
    private val wishlistService: org.booktower.services.WishlistService? = null,
    private val collectionService: org.booktower.services.CollectionService? = null,
    private val webhookService: org.booktower.services.WebhookService? = null,
    private val bookDropService: org.booktower.services.BookDropService? = null,
    private val metadataProposalService: org.booktower.services.MetadataProposalService? = null,
) {
    private fun auth(req: Request) = pageAuth(req, jwtService, authService)

    private fun pc(req: Request) = pageContext(req, jwtService, authService)

    fun discover(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val recommendations = discoveryService?.discover(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("discover.kte", pc.toMap("recommendations" to recommendations)))
    }

    fun readingLists(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val lists = readingListService?.getLists(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("readinglists.kte", pc.toMap("lists" to lists)))
    }

    fun collections(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val collections = collectionService?.getCollections(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("collections.kte", pc.toMap("collections" to collections)))
    }

    fun wishlist(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val items = wishlistService?.getItems(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("wishlist.kte", pc.toMap("items" to items)))
    }

    fun webhooks(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val hooks = webhookService?.list(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("webhooks.kte", pc.toMap("webhooks" to hooks)))
    }

    fun bookDrop(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val pendingFiles = bookDropService?.listPending() ?: emptyList()
        val libraries = libraryService.getLibraries(userId)
        return htmlOk(templateRenderer.render("book-drop.kte", pc.toMap("files" to pendingFiles, "libraries" to libraries)))
    }

    fun metadataProposals(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val allBooks = bookService.getBooks(userId, null, page = 1, pageSize = 500).getBooks()
        val proposals =
            allBooks.flatMap { book ->
                val bookUuid = java.util.UUID.fromString(book.id)
                (metadataProposalService?.listProposals(userId, bookUuid) ?: emptyList())
                    .filter { it.status == "PENDING" }
            }
        return htmlOk(templateRenderer.render("metadata-proposals.kte", pc.toMap("proposals" to proposals)))
    }
}
