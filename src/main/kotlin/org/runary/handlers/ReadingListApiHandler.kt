package org.runary.handlers

import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.CreateReadingListRequest
import org.runary.services.ReadingListService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class ReadingListApiHandler(
    private val readingListService: ReadingListService,
) {
    fun listReadingLists(req: Request): Response {
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(readingListService.getLists(AuthenticatedUser.from(req))),
            )
    }

    fun createReadingList(req: Request): Response {
        val body =
            runCatching {
                Json.mapper
                    .readValue(req.bodyString(), CreateReadingListRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        if (body.name.isBlank()) return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"name is required"}""")
        val list = readingListService.create(AuthenticatedUser.from(req), body)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(list),
            )
    }

    fun getReadingList(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val detail = readingListService.getDetail(AuthenticatedUser.from(req), id) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(detail),
            )
    }

    fun deleteReadingList(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (readingListService.delete(AuthenticatedUser.from(req), id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun addBookToReadingList(req: Request): Response {
        val parts = req.uri.path.split("/")
        val listIdx = parts.indexOf("reading-lists")
        val listId = if (listIdx >= 0 && listIdx + 1 < parts.size) parts[listIdx + 1] else return Response(Status.BAD_REQUEST)
        val bookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (readingListService.addBook(AuthenticatedUser.from(req), listId, bookId)) Response(Status.NO_CONTENT) else Response(Status.CONFLICT)
    }

    fun removeBookFromReadingList(req: Request): Response {
        val parts = req.uri.path.split("/")
        val listIdx = parts.indexOf("reading-lists")
        val listId = if (listIdx >= 0 && listIdx + 1 < parts.size) parts[listIdx + 1] else return Response(Status.BAD_REQUEST)
        val bookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (readingListService.removeBook(AuthenticatedUser.from(req), listId, bookId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun toggleReadingListItem(req: Request): Response {
        val parts = req.uri.path.split("/")
        val listIdx = parts.indexOf("reading-lists")
        val listId = if (listIdx >= 0 && listIdx + 1 < parts.size) parts[listIdx + 1] else return Response(Status.BAD_REQUEST)
        val booksIdx = parts.indexOf("books")
        val bookId = if (booksIdx >= 0 && booksIdx + 1 < parts.size) parts[booksIdx + 1] else return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val completed = body.get("completed")?.asBoolean() ?: return Response(Status.BAD_REQUEST)
        return if (readingListService.toggleCompleted(AuthenticatedUser.from(req), listId, bookId, completed)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun reorderReadingList(req: Request): Response {
        val parts = req.uri.path.split("/")
        val listIdx = parts.indexOf("reading-lists")
        val listId = if (listIdx >= 0 && listIdx + 1 < parts.size) parts[listIdx + 1] else return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookIds = body.get("bookIds")?.map { it.asText() } ?: return Response(Status.BAD_REQUEST)
        return if (readingListService.reorder(AuthenticatedUser.from(req), listId, bookIds)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
