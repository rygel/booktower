package org.booktower.routers

import org.booktower.config.StorageConfig
import org.booktower.filters.AuthenticatedUser
import org.booktower.handlers.BookHandler2
import org.booktower.handlers.BookmarkHandler
import org.booktower.handlers.BulkBookHandler
import org.booktower.handlers.FileHandler
import org.booktower.handlers.FontHandler
import org.booktower.handlers.JournalHandler
import org.booktower.handlers.ReaderPreferencesHandler
import org.booktower.services.AlternativeCoverService
import org.booktower.services.BookDeliveryService
import org.booktower.services.BookFilesService
import org.booktower.services.BookNotebookService
import org.booktower.services.BookReviewService
import org.booktower.services.BookService
import org.booktower.services.ComicMetadataService
import org.booktower.services.ComicService
import org.booktower.services.CreateNotebookRequest
import org.booktower.services.CreateReviewRequest
import org.booktower.services.DuplicateDetectionService
import org.booktower.services.MagicShelfService
import org.booktower.services.RecommendationService
import org.booktower.services.UpdateNotebookRequest
import org.booktower.services.UpdateReviewRequest
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.BookApiRouter")

class BookApiRouter(
    private val filters: FilterSet,
    private val bookHandler: BookHandler2,
    private val bulkBookHandler: BulkBookHandler,
    private val bookmarkHandler: BookmarkHandler,
    private val fileHandler: FileHandler,
    private val bookService: BookService,
    private val comicService: ComicService,
    private val storageConfig: StorageConfig,
    private val magicShelfService: MagicShelfService,
    private val fontHandler: FontHandler?,
    private val readerPreferencesHandler: ReaderPreferencesHandler?,
    private val journalHandler: JournalHandler?,
    private val alternativeCoverService: AlternativeCoverService?,
    private val bookDeliveryService: BookDeliveryService?,
    private val recommendationService: RecommendationService?,
    private val bookFilesService: BookFilesService?,
    private val comicMetadataService: ComicMetadataService?,
    private val communityRatingService: org.booktower.services.CommunityRatingService?,
    private val bookReviewService: BookReviewService?,
    private val bookNotebookService: BookNotebookService?,
    private val duplicateDetectionService: DuplicateDetectionService?,
) {
    @Suppress("LongMethod")
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/api/books" bind Method.GET to filters.auth.then(bookHandler::list),
            "/api/books" bind Method.POST to filters.auth.then(bookHandler::create),
            // Bulk routes must come before /api/books/{id}/... to avoid {id} matching "bulk"
            "/api/books/bulk/move" bind Method.POST to filters.auth.then(bulkBookHandler::move),
            "/api/books/bulk/delete" bind Method.POST to filters.auth.then(bulkBookHandler::delete),
            "/api/books/bulk/tag" bind Method.POST to filters.auth.then(bulkBookHandler::tag),
            "/api/books/bulk/status" bind Method.POST to filters.auth.then(bulkBookHandler::status),
            "/api/books/{id}" bind Method.GET to filters.auth.then(bookHandler::get),
            "/api/books/{id}" bind Method.PUT to filters.auth.then(bookHandler::update),
            "/api/books/{id}" bind Method.DELETE to filters.auth.then(bookHandler::delete),
            "/api/books/{id}/progress" bind Method.PUT to filters.auth.then(bookHandler::updateProgress),
            "/api/books/{id}/status" bind Method.POST to filters.auth.then(::setBookStatus),
            "/api/books/{id}/merge" bind Method.POST to filters.auth.then(::mergeBook),
            "/api/books/{id}/community-rating" bind Method.GET to filters.auth.then(::getCommunityRating),
            "/api/books/{id}/community-rating/fetch" bind Method.POST to filters.auth.then(::fetchCommunityRating),
            "/api/books/{id}/sessions" bind Method.GET to filters.auth.then(bookHandler::sessions),
            "/api/recent" bind Method.GET to filters.auth.then(bookHandler::recent),
            "/api/search" bind Method.GET to filters.auth.then(bookHandler::search),
            "/api/books/{id}/apply-filename-metadata" bind Method.POST to filters.auth.then(::applyFilenameMetadata),
            "/api/books/{id}/apply-sidecar-metadata" bind Method.POST to filters.auth.then(::applySidecarMetadata),
            "/api/formats" bind Method.GET to {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(mapOf("formats" to org.booktower.handlers.SUPPORTED_FORMATS)),
                    )
            },
            "/api/bookmarks" bind Method.GET to filters.auth.then(bookmarkHandler::list),
            "/api/bookmarks" bind Method.POST to filters.auth.then(bookmarkHandler::create),
            "/api/bookmarks/{id}" bind Method.DELETE to filters.auth.then(bookmarkHandler::delete),
            "/api/books/{id}/upload" bind Method.POST to filters.auth.then(fileHandler::upload),
            "/api/books/{id}/cover" bind Method.POST to filters.auth.then(fileHandler::uploadCover),
            "/api/books/{id}/file" bind Method.GET to filters.auth.then(fileHandler::download),
            "/api/books/{id}/kepub" bind Method.GET to filters.auth.then(fileHandler::downloadKepub),
            "/api/books/{id}/read-content" bind Method.GET to filters.auth.then(fileHandler::readContent),
            "/api/books/{id}/audio" bind Method.GET to filters.auth.then(fileHandler::audioStream),
            "/api/books/{id}/chapters" bind Method.GET to filters.auth.then(fileHandler::listChapters),
            "/api/books/{id}/chapters" bind Method.POST to filters.auth.then(fileHandler::uploadChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.GET to filters.auth.then(fileHandler::audioStreamChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.DELETE to filters.auth.then(fileHandler::deleteChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.PUT to filters.auth.then(fileHandler::updateChapterMeta),
            "/api/books/{id}/formats" bind Method.GET to filters.auth.then(::listBookFiles),
            "/api/books/{id}/formats" bind Method.POST to filters.auth.then(::addBookFile),
            "/api/books/{id}/formats/{fileId}" bind Method.DELETE to filters.auth.then(::removeBookFile),
            "/api/books/{id}/comic/pages" bind Method.GET to filters.auth.then(::comicPages),
            "/api/books/{id}/comic/{page}" bind Method.GET to filters.auth.then(::comicPage),
            // Comic/manga reading direction
            "/api/books/{id}/reading-direction" bind Method.PUT to filters.auth.then(::setReadingDirection),
            // Multi-author support
            "/api/books/{id}/authors" bind Method.PUT to filters.auth.then(::setAuthors),
            // Categories
            "/api/books/{id}/categories" bind Method.PUT to filters.auth.then(::setCategories),
            // Moods and extended metadata
            "/api/books/{id}/moods" bind Method.PUT to filters.auth.then(::setMoods),
            "/api/books/{id}/extended-metadata" bind Method.PUT to filters.auth.then(::setExtendedMetadata),
            // External IDs
            "/api/books/{id}/external-ids" bind Method.PUT to filters.auth.then(::setExternalIds),
            // Alternative covers
            "/api/books/{id}/covers/alternatives" bind Method.GET to filters.auth.then(::alternativeCovers),
            "/api/books/{id}/cover/apply-url" bind Method.POST to filters.auth.then(::applyCoverUrl),
            // Book delivery
            "/api/books/{id}/send" bind Method.POST to filters.auth.then(::sendBook),
            "/api/delivery/recipients" bind Method.GET to filters.auth.then(::listRecipients),
            "/api/delivery/recipients" bind Method.POST to filters.auth.then(::addRecipient),
            "/api/delivery/recipients/{id}" bind Method.DELETE to filters.auth.then(::deleteRecipient),
            // Recommendations
            "/api/books/{id}/similar" bind Method.GET to filters.auth.then(::similarBooks),
            // Journal / notebook
            "/api/books/{id}/journal" bind Method.GET to
                filters.auth.then(optionalHandler(journalHandler?.let { it::list })),
            "/api/books/{id}/journal" bind Method.POST to
                filters.auth.then(optionalHandler(journalHandler?.let { it::create })),
            "/api/books/{id}/journal/{entryId}" bind Method.PUT to
                filters.auth.then(optionalHandler(journalHandler?.let { it::update })),
            "/api/books/{id}/journal/{entryId}" bind Method.DELETE to
                filters.auth.then(optionalHandler(journalHandler?.let { it::delete })),
            "/api/journal" bind Method.GET to
                filters.auth.then(optionalHandler(journalHandler?.let { it::listAll })),
            // Custom fonts for EPUB reader
            "/api/fonts" bind Method.GET to filters.auth.then(optionalHandler(fontHandler?.let { it::list })),
            "/api/fonts" bind Method.POST to filters.auth.then(optionalHandler(fontHandler?.let { it::upload })),
            "/api/fonts/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(fontHandler?.let { it::delete })),
            "/fonts/{userId}/{filename}" bind Method.GET to (fontHandler?.let { it::serve } ?: { _ -> Response(Status.NOT_FOUND) }),
            // Per-format reader preferences
            "/api/reader-preferences/{format}" bind Method.GET to
                filters.auth.then(optionalHandler(readerPreferencesHandler?.let { it::get })),
            "/api/reader-preferences/{format}" bind Method.PUT to
                filters.auth.then(optionalHandler(readerPreferencesHandler?.let { it::set })),
            "/api/reader-preferences/{format}" bind Method.PATCH to
                filters.auth.then(optionalHandler(readerPreferencesHandler?.let { it::merge })),
            "/api/reader-preferences/{format}" bind Method.DELETE to
                filters.auth.then(optionalHandler(readerPreferencesHandler?.let { it::delete })),
            // Comic metadata
            "/api/books/{id}/comic-metadata" bind Method.GET to filters.auth.then(::getComicMetadata),
            "/api/books/{id}/comic-metadata" bind Method.PUT to filters.auth.then(::updateComicMetadata),
            // Smart shelves
            "/api/shelves" bind Method.GET to filters.auth.then(::listShelves),
            "/api/shelves" bind Method.POST to filters.auth.then(::createShelf),
            "/api/shelves/{id}/share" bind Method.POST to filters.auth.then(::shareShelf),
            "/api/shelves/{id}/share" bind Method.DELETE to filters.auth.then(::unshareShelf),
            "/public/shelf/{token}" bind Method.GET to ::getPublicShelf,
            // Notebooks
            "/api/books/{id}/notebooks" bind Method.GET to filters.auth.then(::listNotebooks),
            "/api/books/{id}/notebooks" bind Method.POST to filters.auth.then(::createNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.GET to filters.auth.then(::getNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.PUT to filters.auth.then(::updateNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.DELETE to filters.auth.then(::deleteNotebook),
            // Reviews
            "/api/books/{id}/reviews" bind Method.GET to filters.auth.then(::listReviews),
            "/api/books/{id}/reviews" bind Method.POST to filters.auth.then(::createReview),
            "/api/books/{id}/reviews/{reviewId}" bind Method.PUT to filters.auth.then(::updateReview),
            "/api/books/{id}/reviews/{reviewId}" bind Method.DELETE to filters.auth.then(::deleteReview),
        )

    // ─── Inline handler methods ──────────────────────────────────────────────

    private fun setBookStatus(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val statusName = body?.get("status")?.asText()?.takeIf { it.isNotBlank() && it != "NONE" }
        val status =
            statusName?.let { n ->
                org.booktower.models.ReadStatus.entries
                    .firstOrNull { it.name == n }
            }
        bookService.setStatus(userId, bookId, status)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("status" to (status?.name ?: "NONE"))),
            )
    }

    private fun mergeBook(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val targetId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid target book id"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper.readValue(
                    req.bodyString(),
                    org.booktower.models.MergeBookRequest::class.java,
                )
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val sourceId =
            runCatching { java.util.UUID.fromString(body.sourceId) }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid source book id"}""")
        if (!bookService.mergeBooks(userId, targetId, sourceId)) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"One or both books not found, or they are the same book"}""")
        }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"merged":true}""")
    }

    private fun getCommunityRating(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val rating =
            communityRatingService?.getStored(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Community ratings not available"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(rating),
            )
    }

    private fun fetchCommunityRating(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val svc =
            communityRatingService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Community rating service not configured"}""")
        val result =
            svc.fetchAndStore(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"No community rating found for this book"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun applyFilenameMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val fileMeta =
            org.booktower.services.FilenameMetadataService
                .extract(book.filePath ?: book.title)
        val fetchedMeta =
            org.booktower.models.FetchedMetadata(
                title = fileMeta.title,
                author = fileMeta.author,
                description = null,
                isbn = null,
                publisher = null,
                publishedDate = null,
                source = "filename",
            )
        val updated =
            bookService.applyFetchedMetadata(userId, bookId, fetchedMeta)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        if (fileMeta.series != null) {
            bookService.updateSeries(userId, bookId, fileMeta.series, fileMeta.seriesIndex)
        }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("book" to updated, "extracted" to fileMeta),
                ),
            )
    }

    private fun applySidecarMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val sidecar =
            org.booktower.services.SidecarMetadataService
                .read(book.filePath ?: "")
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"No sidecar file found"}""")
        val updated =
            bookService.applyFetchedMetadata(userId, bookId, sidecar)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("book" to updated, "source" to sidecar.source),
                ),
            )
    }

    private fun setReadingDirection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val direction =
            body?.get("direction")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Missing direction field (ltr or rtl)"}""")
        val updated = bookService.setReadingDirection(userId, bookId, direction)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"direction":"${if (direction.lowercase() == "rtl") "rtl" else "ltr"}"}""")
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    private fun setAuthors(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val authors =
            body
                ?.get("authors")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"authors array is required"}""")
        val updated = bookService.setAuthors(userId, bookId, authors)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("authors" to authors)),
                )
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    private fun setCategories(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val categories =
            body
                ?.get("categories")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"categories array is required"}""")
        val updated = bookService.setCategories(userId, bookId, categories)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("categories" to categories)),
                )
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    private fun setMoods(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val moods =
            body
                ?.get("moods")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"moods array is required"}""")
        val updated = bookService.setMoods(userId, bookId, moods)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("moods" to moods)),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    private fun setExtendedMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Request body required"}""")
        val subtitle = body.get("subtitle")?.asText()?.takeIf { it.isNotBlank() }
        val language = body.get("language")?.asText()?.takeIf { it.isNotBlank() }
        val contentRating = body.get("contentRating")?.asText()?.takeIf { it.isNotBlank() }
        val ageRating = body.get("ageRating")?.asText()?.takeIf { it.isNotBlank() }
        val updated = bookService.updateExtendedMetadata(userId, bookId, subtitle, language, contentRating, ageRating)
        return if (updated) {
            val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(book),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    private fun setExternalIds(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Request body required"}""")

        fun str(key: String) = body.get(key)?.asText()?.takeIf { it.isNotBlank() }
        val updated =
            bookService.updateExternalIds(
                userId,
                bookId,
                str("goodreadsId"),
                str("hardcoverId"),
                str("comicvineId"),
                str("openlibraryId"),
                str("googleBooksId"),
                str("amazonId"),
                str("audibleId"),
            )
        return if (updated) {
            val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(book),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    private fun alternativeCovers(req: Request): Response {
        val svc =
            alternativeCoverService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Alternative cover service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val candidates = svc.fetchCandidates(book.title, book.author, book.isbn)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("covers" to candidates)),
            )
    }

    private fun applyCoverUrl(req: Request): Response {
        val svc =
            alternativeCoverService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Alternative cover service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        bookService.getBook(userId, bookId)
            ?: return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val url =
            body?.get("url")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"url is required"}""")
        val ext =
            url
                .substringAfterLast('.', "jpg")
                .lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val bytes =
            svc.downloadBytes(url)
                ?: return Response(Status.BAD_GATEWAY)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Failed to download cover from URL"}""")
        val coversDir = java.io.File(storageConfig.coversPath)
        if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create covers directory: ${coversDir.absolutePath}")
        val coverFilename = "$bookId.$ext"
        java.io.File(coversDir, coverFilename).writeBytes(bytes)
        bookService.updateCoverPath(userId, bookId, coverFilename)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("coverUrl" to "/covers/$coverFilename")),
            )
    }

    private fun sendBook(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val toEmail =
            body?.get("email")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"email is required"}""")
        return try {
            svc.sendBook(userId, bookId, toEmail)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"sent":true}""")
        } catch (e: IllegalArgumentException) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("error" to e.message)),
                )
        }
    }

    private fun listRecipients(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("recipients" to svc.listRecipients(userId))),
            )
    }

    private fun addRecipient(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val label =
            body?.get("label")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"label is required"}""")
        val email =
            body.get("email")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"email is required"}""")
        return try {
            val recipient = svc.addRecipient(userId, org.booktower.services.AddRecipientRequest(label, email))
            Response(Status.CREATED)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(recipient),
                )
        } catch (e: IllegalArgumentException) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("error" to e.message)),
                )
        }
    }

    private fun deleteRecipient(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val recipientId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid recipient ID"}""")
        return if (svc.deleteRecipient(userId, recipientId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Recipient not found"}""")
        }
    }

    private fun similarBooks(req: Request): Response {
        val svc =
            recommendationService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Recommendation service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val similar = svc.findSimilar(userId, bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("similar" to similar)),
            )
    }

    private fun listBookFiles(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listFiles(bookId)),
            )
    }

    private fun addBookFile(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), org.booktower.models.AddBookFileRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")

        return runCatching { svc.addFile(bookId, request) }
            .fold(
                onSuccess = { dto ->
                    Response(Status.CREATED)
                        .header("Content-Type", "application/json")
                        .body(
                            org.booktower.config.Json.mapper
                                .writeValueAsString(dto),
                        )
                },
                onFailure = { e ->
                    Response(Status.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"${e.message}"}""")
                },
            )
    }

    private fun removeBookFile(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val fileId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val removed = svc.removeFile(bookId, fileId)
        return if (removed) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun comicPages(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val count = comicService.getPageCount(filePath)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"pageCount":$count}""")
    }

    private fun comicPage(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val pageIndex = parts.lastOrNull()?.toIntOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId =
            parts
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bytes = comicService.getPage(filePath, pageIndex) ?: return Response(Status.NOT_FOUND)
        val pages = comicService.listPages(filePath)
        val mime = pages.getOrNull(pageIndex)?.contentType ?: "image/jpeg"
        return Response(Status.OK)
            .header("Content-Type", mime)
            .header("Cache-Control", "private, max-age=3600")
            .body(bytes.inputStream())
    }

    private fun getComicMetadata(req: Request): Response {
        val svc = comicMetadataService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(meta),
            )
    }

    private fun updateComicMetadata(req: Request): Response {
        val svc = comicMetadataService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), org.booktower.models.ComicMetadataRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated = svc.update(bookId, request)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(updated),
            )
    }

    // ─── Shelves ──────────────────────────────────────────────────────────────

    private fun listShelves(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelves = magicShelfService.getShelves(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelves),
            )
    }

    private fun createShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val name =
            body
                .get("name")
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
        val ruleTypeStr =
            body.get("ruleType")?.asText()?.trim()
                ?: return Response(Status.BAD_REQUEST)
        val ruleType =
            runCatching {
                org.booktower.models.ShelfRuleType
                    .valueOf(ruleTypeStr)
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val ruleValue = body.get("ruleValue")?.takeIf { !it.isNull }?.asText()
        val shelf = magicShelfService.createShelf(userId, org.booktower.models.CreateMagicShelfRequest(name, ruleType, ruleValue))
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }

    private fun shareShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelfId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val shelf =
            magicShelfService.shareShelf(userId, shelfId)
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }

    private fun unshareShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelfId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        return if (magicShelfService.unshareShelf(userId, shelfId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    private fun getPublicShelf(req: Request): Response {
        val token =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
        val shelf =
            magicShelfService.getPublicShelf(token)
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }

    // ─── Notebooks ────────────────────────────────────────────────────────────

    private fun listNotebooks(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listForBook(bookId, userId)),
            )
    }

    private fun createNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateNotebookRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(bookId, userId, request) }
            .fold(
                onSuccess = {
                    Response(
                        Status.CREATED,
                    ).header("Content-Type", "application/json").body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(it),
                    )
                },
                onFailure = { e ->
                    Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                },
            )
    }

    private fun getNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val nb = svc.get(bookId, notebookId, userId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK).header("Content-Type", "application/json").body(
            org.booktower.config.Json.mapper
                .writeValueAsString(nb),
        )
    }

    private fun updateNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateNotebookRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated =
            runCatching { svc.update(bookId, notebookId, userId, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                }
                ?: return Response(Status.NOT_FOUND)
        return Response(
            Status.OK,
        ).header("Content-Type", "application/json").body(
            org.booktower.config.Json.mapper
                .writeValueAsString(updated),
        )
    }

    private fun deleteNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId, notebookId, userId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Reviews ──────────────────────────────────────────────────────────────

    private fun listReviews(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listForBook(bookId)),
            )
    }

    private fun createReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateReviewRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(bookId, userId, request) }
            .fold(
                onSuccess = {
                    Response(
                        Status.CREATED,
                    ).header("Content-Type", "application/json").body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(it),
                    )
                },
                onFailure = { e ->
                    Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                },
            )
    }

    private fun updateReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val reviewId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateReviewRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated =
            runCatching { svc.update(bookId, reviewId, userId, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                }
                ?: return Response(Status.NOT_FOUND)
        return Response(
            Status.OK,
        ).header("Content-Type", "application/json").body(
            org.booktower.config.Json.mapper
                .writeValueAsString(updated),
        )
    }

    private fun deleteReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val reviewId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId, reviewId, userId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
