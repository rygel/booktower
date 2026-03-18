package org.booktower.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.models.CommunityRatingDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.CommunityRatingService")
private val mapper = ObjectMapper()

open class CommunityRatingService(
    private val jdbi: Jdbi,
) {
    protected open val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    /**
     * Fetches community rating for the given book from external sources.
     * Tries Google Books first (by googleBooksId or ISBN), then OpenLibrary.
     * Persists the result and returns it, or null if nothing was found.
     */
    fun fetchAndStore(
        userId: UUID,
        bookId: UUID,
    ): CommunityRatingDto? {
        val book =
            jdbi.withHandle<Map<String, String?>, Exception> { h ->
                h
                    .createQuery(
                        """
                SELECT b.isbn, b.google_books_id, b.openlibrary_id, b.title
                FROM books b
                INNER JOIN libraries l ON l.id = b.library_id
                WHERE b.id = ? AND l.user_id = ?
            """,
                    ).bind(0, bookId.toString())
                    .bind(1, userId.toString())
                    .map { r ->
                        mapOf(
                            "isbn" to r.getColumn("isbn", String::class.java),
                            "googleBooksId" to r.getColumn("google_books_id", String::class.java),
                            "openlibraryId" to r.getColumn("openlibrary_id", String::class.java),
                            "title" to r.getColumn("title", String::class.java),
                        )
                    }.firstOrNull()
            } ?: return null

        val result =
            fetchFromGoogleBooks(book["googleBooksId"], book["isbn"], book["title"])
                ?: fetchFromOpenLibrary(book["openlibraryId"], book["isbn"])

        if (result != null) {
            val now = Instant.now().toString()
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """
                    UPDATE books SET community_rating = ?, community_rating_count = ?,
                        community_rating_source = ?, community_rating_fetched_at = ?
                    WHERE id = ?
                """,
                    ).bind(0, result.rating)
                    .bind(1, result.count)
                    .bind(2, result.source)
                    .bind(3, now)
                    .bind(4, bookId.toString())
                    .execute()
            }
            return result.copy(fetchedAt = now)
        }
        return null
    }

    fun getStored(
        userId: UUID,
        bookId: UUID,
    ): CommunityRatingDto? =
        jdbi.withHandle<CommunityRatingDto?, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT b.community_rating, b.community_rating_count,
                       b.community_rating_source, b.community_rating_fetched_at
                FROM books b
                INNER JOIN libraries l ON l.id = b.library_id
                WHERE b.id = ? AND l.user_id = ?
            """,
                ).bind(0, bookId.toString())
                .bind(1, userId.toString())
                .map { r ->
                    CommunityRatingDto(
                        rating = r.getColumn("community_rating", java.lang.Double::class.java)?.toDouble(),
                        count = r.getColumn("community_rating_count", java.lang.Integer::class.java)?.toInt(),
                        source = r.getColumn("community_rating_source", String::class.java),
                        fetchedAt = r.getColumn("community_rating_fetched_at", String::class.java),
                    )
                }.firstOrNull()
        }

    protected open fun fetchFromGoogleBooks(
        googleBooksId: String?,
        isbn: String?,
        title: String?,
    ): CommunityRatingDto? {
        val url =
            when {
                !googleBooksId.isNullOrBlank() ->
                    "https://www.googleapis.com/books/v1/volumes/$googleBooksId"
                !isbn.isNullOrBlank() ->
                    "https://www.googleapis.com/books/v1/volumes?q=isbn:${URLEncoder.encode(isbn, "UTF-8")}&maxResults=1"
                !title.isNullOrBlank() ->
                    "https://www.googleapis.com/books/v1/volumes?q=intitle:${URLEncoder.encode(title, "UTF-8")}&maxResults=1"
                else -> return null
            }
        return runCatching {
            val req =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return null
            val json = mapper.readTree(resp.body())
            val volumeInfo =
                if (json.has("items")) {
                    json.get("items")?.get(0)?.get("volumeInfo")
                } else {
                    json.get("volumeInfo")
                }
            val rating = volumeInfo?.get("averageRating")?.asDouble()
            val count = volumeInfo?.get("ratingsCount")?.asInt()
            if (rating != null) CommunityRatingDto(rating, count, "googlebooks", null) else null
        }.getOrElse { e ->
            logger.debug("Google Books rating fetch failed: ${e.message}")
            null
        }
    }

    protected open fun fetchFromOpenLibrary(
        openLibraryId: String?,
        isbn: String?,
    ): CommunityRatingDto? {
        val worksId =
            when {
                !openLibraryId.isNullOrBlank() -> openLibraryId.removePrefix("/works/")
                !isbn.isNullOrBlank() -> resolveOpenLibraryWorksId(isbn) ?: return null
                else -> return null
            }
        return runCatching {
            val url = "https://openlibrary.org/works/$worksId/ratings.json"
            val req =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return null
            val json = mapper.readTree(resp.body())
            val summary = json.get("summary")
            val rating = summary?.get("average")?.asDouble()
            val count = summary?.get("count")?.asInt()
            if (rating != null && rating > 0) CommunityRatingDto(rating, count, "openlibrary", null) else null
        }.getOrElse { e ->
            logger.debug("OpenLibrary rating fetch failed: ${e.message}")
            null
        }
    }

    private fun resolveOpenLibraryWorksId(isbn: String): String? {
        return runCatching {
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"
            val req =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return null
            val json = mapper.readTree(resp.body())
            val entry = json.get("ISBN:$isbn") ?: return null
            entry.get("key")?.asText()?.removePrefix("/works/")
        }.getOrElse { null }
    }
}
