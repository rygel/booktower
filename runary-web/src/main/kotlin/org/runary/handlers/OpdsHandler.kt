package org.runary.handlers

import com.github.benmanes.caffeine.cache.Caffeine
import org.runary.config.StorageConfig
import org.runary.models.BookDto
import org.runary.models.BookFileDto
import org.runary.models.LibraryDto
import org.runary.services.ApiTokenService
import org.runary.services.AuthService
import org.runary.services.BookService
import org.runary.services.LibraryService
import org.runary.services.OpdsCredentialsService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

private val OPDS_CONTENT_TYPES =
    mapOf(
        "pdf" to "application/pdf",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "cbz" to "application/vnd.comicbook+zip",
        "cbr" to "application/vnd.comicbook-rar",
        "fb2" to "application/x-fictionbook+xml",
    )

/**
 * OPDS Catalog 1.2 handler.
 *
 * Exposes three endpoints authenticated via HTTP Basic Auth
 * (username:password, matching existing Runary credentials):
 *
 *   GET /opds/catalog              – navigation feed listing all libraries
 *   GET /opds/catalog/{libraryId}  – acquisition feed listing books in a library
 *   GET /opds/books/{id}/file      – file download (Basic Auth, same as above)
 *
 * OPDS clients (KOReader, Kybook, Aldiko, etc.) send Basic Auth on every
 * request, so all three endpoints require it. JWT cookies are NOT used.
 */
class OpdsHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val storageConfig: StorageConfig,
    private val apiTokenService: ApiTokenService,
    private val opdsCredentialsService: OpdsCredentialsService? = null,
) {
    /** Cache library lists per user — libraries change rarely. */
    private val libraryCache =
        Caffeine
            .newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<UUID, List<LibraryDto>>()

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Accepts HTTP Basic Auth or Bearer API token.
     * For Basic Auth, tries OPDS-specific credentials first (if configured for that username),
     * then falls back to main account credentials.
     */
    private fun authenticate(req: Request): UUID? {
        val auth = req.header("Authorization") ?: return null
        if (auth.startsWith("Bearer ")) {
            val rawToken = auth.removePrefix("Bearer ").trim()
            return apiTokenService.validateToken(rawToken)
        }
        if (!auth.startsWith("Basic ")) return null
        return try {
            val decoded = String(Base64.getDecoder().decode(auth.removePrefix("Basic ")))
            val colonIdx = decoded.indexOf(':')
            if (colonIdx < 0) return null
            val username = decoded.substring(0, colonIdx)
            val password = decoded.substring(colonIdx + 1)
            // Try OPDS-specific credentials first
            opdsCredentialsService?.authenticate(username, password)
                ?: authService.getUserByCredentials(username, password)?.id
        } catch (_: Exception) {
            null
        }
    }

    private fun unauthorized(): Response =
        Response(Status.UNAUTHORIZED)
            .header("WWW-Authenticate", """Basic realm="Runary OPDS", charset="UTF-8"""")
            .header("Content-Type", "text/plain; charset=utf-8")
            .body("Authentication required")

    // ── Root catalog (navigation feed) ───────────────────────────────────────

    /** GET /opds/catalog */
    fun catalog(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val libraries = libraryCache.get(userId) { libraryService.getLibraries(it) }
        val updated = Instant.now().toString()

        val entries =
            libraries.joinToString("\n") { lib ->
                """  <entry>
    <id>urn:runary:library:${lib.id}</id>
    <title>${x(lib.name)}</title>
    <updated>$updated</updated>
    <content type="text">${lib.bookCount} books</content>
    <link rel="subsection"
          href="/opds/catalog/${lib.id}"
          type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
  </entry>"""
            }

        return opdsResponse(
            contentType = "application/atom+xml;profile=opds-catalog;kind=navigation",
            body =
                navigationFeed(
                    id = "urn:runary:catalog",
                    title = "Runary",
                    selfHref = "/opds/catalog",
                    selfKind = "navigation",
                    updated = updated,
                    extraLinks = "",
                    entries = entries,
                ),
        )
    }

    // ── Library acquisition feed ──────────────────────────────────────────────

    /** GET /opds/catalog/{libraryId} */
    fun library(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val libIdStr =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .lastOrNull()
                ?: return Response(Status.NOT_FOUND)
        val libId =
            try {
                UUID.fromString(libIdStr)
            } catch (_: Exception) {
                return Response(Status.NOT_FOUND)
            }
        val library = libraryService.getLibrary(userId, libId) ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooks(userId, libId.toString(), 1, 500).getBooks()
        val updated = Instant.now().toString()

        val bookIds = books.map { UUID.fromString(it.id) }
        val allFiles = bookService.getBookFilesForBooks(userId, bookIds)

        val entries =
            books.joinToString("\n") { book ->
                bookEntry(book, updated, allFiles[book.id] ?: emptyList())
            }

        val upLink = """  <link rel="up"
        href="/opds/catalog"
        type="application/atom+xml;profile=opds-catalog;kind=navigation"/>"""

        return opdsResponse(
            contentType = "application/atom+xml;profile=opds-catalog;kind=acquisition",
            body =
                navigationFeed(
                    id = "urn:runary:library:${library.id}",
                    title = library.name,
                    selfHref = "/opds/catalog/${library.id}",
                    selfKind = "acquisition",
                    updated = updated,
                    extraLinks = upLink,
                    entries = entries,
                ),
        )
    }

    // ── Chapter streaming (Basic Auth) ───────────────────────────────────────

    /** GET /opds/books/{id}/chapters/{trackIndex} */
    fun streamChapter(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val pathParts = req.uri.path.split("/")
        val trackIndex =
            pathParts.lastOrNull()?.toIntOrNull()
                ?: return Response(Status.NOT_FOUND)
        val bookId =
            try {
                UUID.fromString(pathParts.dropLast(2).lastOrNull() ?: return Response(Status.NOT_FOUND))
            } catch (_: Exception) {
                return Response(Status.NOT_FOUND)
            }

        val filePath =
            bookService.getBookFilePath(userId, bookId, trackIndex)
                ?: return Response(Status.NOT_FOUND)
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return Response(Status.NOT_FOUND)

        val contentType =
            when (file.extension.lowercase()) {
                "m4a", "m4b", "aac" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/mpeg"
            }
        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Length", file.length().toString())
            .header("Accept-Ranges", "bytes")
            .body(file.inputStream())
    }

    // ── File download (Basic Auth) ────────────────────────────────────────────

    /** GET /opds/books/{id}/file */
    fun download(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val bookIdStr =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?: return Response(Status.NOT_FOUND)
        val bookId =
            try {
                UUID.fromString(bookIdStr)
            } catch (_: Exception) {
                return Response(Status.NOT_FOUND)
            }
        val filePath =
            bookService.getBookFilePath(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
        if (filePath.isBlank()) return Response(Status.NOT_FOUND)

        val file = File(filePath)
        if (!file.exists() || !file.isFile) return Response(Status.NOT_FOUND)

        val ext = file.extension.lowercase()
        val contentType = OPDS_CONTENT_TYPES[ext] ?: "application/octet-stream"

        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }

    // ── XML helpers ───────────────────────────────────────────────────────────

    private fun bookEntry(
        book: BookDto,
        updated: String,
        chapters: List<BookFileDto> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("  <entry>\n")
        sb.append("    <id>urn:uuid:${book.id}</id>\n")
        sb.append("    <title>${x(book.title)}</title>\n")
        sb.append("    <updated>$updated</updated>\n")
        book.author?.takeIf { it.isNotBlank() }?.let { author ->
            sb.append("    <author><name>${x(author)}</name></author>\n")
        }
        book.description?.takeIf { it.isNotBlank() }?.let { desc ->
            sb.append("    <content type=\"text\">${x(desc)}</content>\n")
        }
        book.isbn?.takeIf { it.isNotBlank() }?.let { isbn ->
            sb.append("    <dc:identifier>urn:isbn:${x(isbn)}</dc:identifier>\n")
        }
        book.publisher?.takeIf { it.isNotBlank() }?.let { pub ->
            sb.append("    <dc:publisher>${x(pub)}</dc:publisher>\n")
        }
        book.publishedDate?.takeIf { it.isNotBlank() }?.let { date ->
            sb.append("    <dc:date>${x(date)}</dc:date>\n")
        }
        book.coverUrl?.let { url ->
            sb.append("    <link rel=\"http://opds-spec.org/image\"\n")
            sb.append("          href=\"${x(url)}\"\n")
            sb.append("          type=\"image/jpeg\"/>\n")
        }
        book.filePath?.takeIf { it.isNotBlank() }?.let {
            sb.append("    <link rel=\"http://opds-spec.org/acquisition\"\n")
            sb.append("          href=\"/opds/books/${book.id}/file\"\n")
            sb.append("          type=\"application/octet-stream\"/>\n")
        }
        for (ch in chapters.sortedBy { it.trackIndex }) {
            val chTitle = x(ch.title ?: "Chapter ${ch.trackIndex + 1}")
            val ext = ch.filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
            val mimeType =
                when (ext) {
                    "m4a", "m4b", "aac" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    else -> "audio/mpeg"
                }
            sb.append("    <link rel=\"http://opds-spec.org/acquisition\"\n")
            sb.append("          href=\"/opds/books/${book.id}/chapters/${ch.trackIndex}\"\n")
            sb.append("          type=\"$mimeType\"\n")
            sb.append("          title=\"$chTitle\"/>\n")
        }
        sb.append("  </entry>")
        return sb.toString()
    }

    private fun navigationFeed(
        id: String,
        title: String,
        selfHref: String,
        selfKind: String,
        updated: String,
        extraLinks: String,
        entries: String,
    ): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<feed xmlns="http://www.w3.org/2005/Atom"""")
            appendLine("""      xmlns:opds="http://opds-spec.org/2010/catalog"""")
            appendLine("""      xmlns:dc="http://purl.org/dc/terms/">""")
            appendLine("  <id>$id</id>")
            appendLine("  <title>${x(title)}</title>")
            appendLine("  <updated>$updated</updated>")
            appendLine("  <author><name>Runary</name></author>")
            appendLine("""  <link rel="self"""")
            appendLine("""        href="$selfHref"""")
            appendLine("""        type="application/atom+xml;profile=opds-catalog;kind=$selfKind"/>""")
            appendLine("""  <link rel="start"""")
            appendLine("""        href="/opds/catalog"""")
            appendLine("""        type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            if (extraLinks.isNotBlank()) {
                appendLine(extraLinks)
            }
            if (entries.isNotBlank()) {
                appendLine(entries)
            }
            append("</feed>")
        }

    private fun opdsResponse(
        contentType: String,
        body: String,
    ): Response =
        Response(Status.OK)
            .header("Content-Type", "$contentType; charset=utf-8")
            .body(body)

    // ── OPDS 2.0 (JSON, Readium Web Publication) ──────────────────────────

    private val jsonContentType = "application/opds+json"

    /** GET /opds/v2/catalog — OPDS 2.0 navigation feed (JSON). */
    fun catalogV2(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val libraries = libraryCache.get(userId) { libraryService.getLibraries(it) }
        val navigation =
            libraries.map { lib ->
                mapOf(
                    "href" to "/opds/v2/catalog/${lib.id}",
                    "title" to lib.name,
                    "type" to jsonContentType,
                    "properties" to mapOf("numberOfItems" to lib.bookCount),
                )
            }
        val feed =
            mapOf(
                "metadata" to mapOf("title" to "Runary"),
                "links" to
                    listOf(
                        mapOf("rel" to "self", "href" to "/opds/v2/catalog", "type" to jsonContentType),
                    ),
                "navigation" to navigation,
            )
        return Response(Status.OK)
            .header("Content-Type", jsonContentType)
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(feed),
            )
    }

    /** GET /opds/v2/catalog/{libraryId} — OPDS 2.0 publication list (JSON). */
    fun libraryV2(req: Request): Response {
        val userId = authenticate(req) ?: return unauthorized()
        val libIdStr =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .lastOrNull()
                ?: return Response(Status.NOT_FOUND)
        val libId =
            try {
                UUID.fromString(libIdStr)
            } catch (_: Exception) {
                return Response(Status.NOT_FOUND)
            }
        val library = libraryService.getLibrary(userId, libId) ?: return Response(Status.NOT_FOUND)
        val books = bookService.getBooks(userId, libId.toString(), 1, 500).getBooks()
        val bookIds = books.map { UUID.fromString(it.id) }
        val allFiles = bookService.getBookFilesForBooks(userId, bookIds)

        val publications =
            books.map { book ->
                publicationEntry(book, allFiles[book.id] ?: emptyList())
            }

        val feed =
            mapOf(
                "metadata" to mapOf("title" to library.name, "numberOfItems" to books.size),
                "links" to
                    listOf(
                        mapOf("rel" to "self", "href" to "/opds/v2/catalog/${library.id}", "type" to jsonContentType),
                        mapOf("rel" to "up", "href" to "/opds/v2/catalog", "type" to jsonContentType),
                    ),
                "publications" to publications,
            )
        return Response(Status.OK)
            .header("Content-Type", jsonContentType)
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(feed),
            )
    }

    private fun publicationEntry(
        book: BookDto,
        chapters: List<BookFileDto>,
    ): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>("title" to book.title, "@type" to "http://schema.org/Book")
        book.author?.let { metadata["author"] = mapOf("name" to it) }
        book.description?.let { metadata["description"] = it }
        book.isbn?.let { metadata["identifier"] = "urn:isbn:$it" }
        book.publisher?.let { metadata["publisher"] = mapOf("name" to it) }
        book.publishedDate?.let { metadata["published"] = it }
        book.language?.let { metadata["language"] = it }
        book.pageCount?.let { metadata["numberOfPages"] = it }

        val links = mutableListOf<Map<String, Any?>>()
        book.filePath?.takeIf { it.isNotBlank() }?.let { fp ->
            val ext = fp.substringAfterLast('.', "").lowercase()
            links +=
                mapOf(
                    "rel" to "http://opds-spec.org/acquisition",
                    "href" to "/opds/books/${book.id}/file",
                    "type" to (OPDS_CONTENT_TYPES[ext] ?: "application/octet-stream"),
                )
        }
        for (ch in chapters.sortedBy { it.trackIndex }) {
            val ext = ch.filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
            val mimeType =
                when (ext) {
                    "m4a", "m4b", "aac" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    else -> "audio/mpeg"
                }
            links +=
                mapOf(
                    "rel" to "http://opds-spec.org/acquisition",
                    "href" to "/opds/books/${book.id}/chapters/${ch.trackIndex}",
                    "type" to mimeType,
                    "title" to (ch.title ?: "Chapter ${ch.trackIndex + 1}"),
                )
        }

        val images = mutableListOf<Map<String, String>>()
        book.coverUrl?.let { url ->
            images += mapOf("href" to url, "type" to "image/jpeg")
        }

        return mapOf(
            "metadata" to metadata,
            "links" to links,
            "images" to images,
        )
    }

    /** XML-escape a string value. */
    private fun x(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
