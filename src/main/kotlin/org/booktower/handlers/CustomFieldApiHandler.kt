package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.services.CreateFieldDefinitionRequest
import org.booktower.services.CustomFieldService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class CustomFieldApiHandler(
    private val customFieldService: CustomFieldService,
) {
    fun listFieldDefinitions(req: Request): Response {
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(customFieldService.getDefinitions(AuthenticatedUser.from(req))),
            )
    }

    fun createFieldDefinition(req: Request): Response {
        val body =
            runCatching {
                Json.mapper
                    .readValue(req.bodyString(), CreateFieldDefinitionRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        if (body.fieldName.isBlank()) return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"fieldName is required"}""")
        return try {
            val def = customFieldService.createDefinition(AuthenticatedUser.from(req), body)
            Response(Status.CREATED)
                .header("Content-Type", "application/json")
                .body(
                    Json.mapper
                        .writeValueAsString(def),
                )
        } catch (e: Exception) {
            Response(Status.CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"Field already exists: ${body.fieldName}"}""")
        }
    }

    fun deleteFieldDefinition(req: Request): Response {
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (customFieldService.deleteDefinition(AuthenticatedUser.from(req), id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    fun getBookCustomFields(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(customFieldService.getValues(userId, bookId)),
            )
    }

    fun setBookCustomField(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val fieldName = body.get("fieldName")?.asText()?.takeIf { it.isNotBlank() } ?: return Response(Status.BAD_REQUEST)
        val fieldValue = body.get("fieldValue")?.asText()
        customFieldService.setValue(userId, bookId, fieldName, fieldValue)
        return Response(Status.NO_CONTENT)
    }

    fun deleteBookCustomField(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val parts = req.uri.path.split("/")
        val bookId =
            parts
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val fieldName = java.net.URLDecoder.decode(parts.lastOrNull() ?: return Response(Status.BAD_REQUEST), "UTF-8")
        return if (customFieldService.deleteValue(userId, bookId, fieldName)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
