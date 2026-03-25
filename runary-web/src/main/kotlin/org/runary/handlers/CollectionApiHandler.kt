package org.runary.handlers

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.CollectionService
import org.runary.services.CreateCollectionRequest

class CollectionApiHandler(
    private val collectionService: CollectionService,
) {
    fun listCollections(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(collectionService.getCollections(userId)),
            )
    }

    fun createCollection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val name = req.form("name")?.trim() ?: return Response(Status.BAD_REQUEST)
        if (name.isBlank() || name.length > 100) return Response(Status.BAD_REQUEST)
        val desc = req.form("description")?.trim()?.takeIf { it.isNotBlank() }
        val collection = collectionService.createCollection(userId, CreateCollectionRequest(name, desc))
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(collection),
            )
    }

    fun deleteCollection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        return if (collectionService.deleteCollection(userId, id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun addBookToCollection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val parts = req.uri.path.split("/")
        val collId = parts.getOrNull(3)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.getOrNull(5)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return if (collectionService.addBook(userId, collId, bookId)) Response(Status.OK) else Response(Status.NOT_FOUND)
    }

    fun removeBookFromCollection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val parts = req.uri.path.split("/")
        val collId = parts.getOrNull(3)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.getOrNull(5)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return if (collectionService.removeBook(userId, collId, bookId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
