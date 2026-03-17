package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.services.FontService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import java.util.UUID

class FontHandler(private val fontService: FontService) {

    /** GET /api/fonts — list user's uploaded fonts. */
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val fonts = fontService.listFonts(userId)
        val body = fonts.map { mapOf("id" to it.id, "originalName" to it.originalName, "filename" to it.filename, "fileSize" to it.fileSize, "url" to it.url, "createdAt" to it.createdAt.toString()) }
        return Response(Status.OK).header("Content-Type", "application/json").body(Json.mapper.writeValueAsString(body))
    }

    /** POST /api/fonts — upload a font file (multipart or raw body). */
    fun upload(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val contentDisposition = req.header("Content-Disposition") ?: ""
        val originalName = contentDisposition.split(";")
            .firstOrNull { it.trim().startsWith("filename") }
            ?.substringAfter("filename=")?.trim()?.trim('"')
            ?: req.header("X-Font-Name")
            ?: "font.ttf"
        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) return Response(Status.BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"Empty file"}""")
        return when (val result = fontService.uploadFont(userId, originalName, bytes)) {
            is kotlin.Result<*> -> if (result.isSuccess) {
                val f = result.getOrThrow() as org.booktower.services.UserFont
                Response(Status.CREATED).header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(mapOf("id" to f.id, "originalName" to f.originalName, "url" to f.url, "fileSize" to f.fileSize)))
            } else {
                Response(Status.BAD_REQUEST).header("Content-Type", "application/json")
                    .body("""{"error":"${result.exceptionOrNull()?.message}"}""")
            }
        }
    }

    /** DELETE /api/fonts/{id} — delete a font. */
    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val fontId = req.path("id") ?: return Response(Status.BAD_REQUEST)
        return if (fontService.deleteFont(userId, fontId)) Response(Status.NO_CONTENT)
        else Response(Status.NOT_FOUND)
    }

    /** GET /fonts/{userId}/{filename} — serve a font file (public to allow browser CSS @font-face). */
    fun serve(req: Request): Response {
        val userIdStr = req.path("userId") ?: return Response(Status.NOT_FOUND)
        val filename = req.path("filename") ?: return Response(Status.NOT_FOUND)
        val userId = runCatching { UUID.fromString(userIdStr) }.getOrNull() ?: return Response(Status.NOT_FOUND)
        val file = fontService.getFontFile(userId, filename) ?: return Response(Status.NOT_FOUND)
        val ext = filename.substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "ttf"   -> "font/ttf"
            "otf"   -> "font/otf"
            "woff"  -> "font/woff"
            "woff2" -> "font/woff2"
            else    -> "application/octet-stream"
        }
        return Response(Status.OK)
            .header("Content-Type", mimeType)
            .header("Cache-Control", "public, max-age=31536000")
            .body(file.readBytes().let { org.http4k.core.Body(it.inputStream()) })
    }
}
