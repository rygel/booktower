package org.runary.handlers

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.CreateWebhookRequest
import org.runary.services.WebhookService

class WebhookApiHandler(
    private val webhookService: WebhookService,
) {
    fun listWebhooks(req: Request): Response =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(webhookService.list(AuthenticatedUser.from(req))),
            )

    fun createWebhook(req: Request): Response {
        val body =
            runCatching {
                Json.mapper
                    .readValue(req.bodyString(), CreateWebhookRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid request body"}""")
        if (body.url.isBlank() || body.name.isBlank() || body.events.isEmpty()) return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"name, url, and events are required"}""")
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(webhookService.create(AuthenticatedUser.from(req), body)),
            )
    }

    fun deleteWebhook(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (webhookService.delete(AuthenticatedUser.from(req), id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun toggleWebhook(req: Request): Response {
        val parts = req.uri.path.split("/")
        val id = parts.dropLast(1).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val enabled = body?.get("enabled")?.asBoolean() ?: return Response(Status.BAD_REQUEST)
        return if (webhookService.toggle(AuthenticatedUser.from(req), id, enabled)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
