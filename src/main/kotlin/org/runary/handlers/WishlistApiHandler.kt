package org.runary.handlers

import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.CreateWishlistItemRequest
import org.runary.services.WishlistService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class WishlistApiHandler(
    private val wishlistService: WishlistService,
) {
    fun listWishlist(req: Request): Response {
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(wishlistService.getItems(AuthenticatedUser.from(req))),
            )
    }

    fun addToWishlist(req: Request): Response {
        val body =
            runCatching {
                Json.mapper
                    .readValue(req.bodyString(), CreateWishlistItemRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        if (body.title.isBlank()) return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"title is required"}""")
        val item = wishlistService.addItem(AuthenticatedUser.from(req), body)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(item),
            )
    }

    fun updateWishlistItem(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                Json.mapper
                    .readValue(req.bodyString(), CreateWishlistItemRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (wishlistService.updateItem(AuthenticatedUser.from(req), id, body)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun deleteWishlistItem(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (wishlistService.deleteItem(AuthenticatedUser.from(req), id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
