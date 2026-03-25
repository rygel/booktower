package org.runary.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val hcLogger = LoggerFactory.getLogger("runary.HardcoverSyncService")
private val hcMapper = ObjectMapper()

/** Hardcover reading status IDs. */
private object HardcoverStatus {
    const val WANT_TO_READ = 1
    const val CURRENTLY_READING = 2
    const val READ = 3
    const val DID_NOT_FINISH = 4
}

data class HardcoverBookMapping(
    val userId: UUID,
    val bookId: UUID,
    val hardcoverBookId: Int,
    val hardcoverEditionId: Int?,
    val lastSyncedAt: String?,
)

data class HardcoverSyncResult(
    val synced: Boolean,
    val hardcoverBookId: Int?,
    val message: String,
)

class HardcoverSyncService(
    private val jdbi: Jdbi,
    private val userSettingsService: UserSettingsService,
) {
    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    private val apiUrl = "https://api.hardcover.app/v1/graphql"
    private val apiKeySettingKey = "hardcover_api_key"

    // ── API key management ────────────────────────────────────────────────────

    fun setApiKey(
        userId: UUID,
        apiKey: String,
    ) {
        userSettingsService.set(userId, apiKeySettingKey, apiKey.ifBlank { null })
    }

    fun hasApiKey(userId: UUID): Boolean = userSettingsService.get(userId, apiKeySettingKey)?.isNotBlank() == true

    private fun getApiKey(userId: UUID): String? = userSettingsService.get(userId, apiKeySettingKey)?.takeIf { it.isNotBlank() }

    // ── Connection test ───────────────────────────────────────────────────────

    /** Verifies the API key by querying the current user. Returns the Hardcover username or null on failure. */
    fun testConnection(userId: UUID): String? {
        val key = getApiKey(userId) ?: return null
        return try {
            val result = graphql(key, """query { me { username } }""", emptyMap())
            result
                ?.path("data")
                ?.path("me")
                ?.path("username")
                ?.asText()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            hcLogger.warn("Hardcover connection test failed for user $userId: ${e.message}")
            null
        }
    }

    // ── Book mapping ──────────────────────────────────────────────────────────

    fun getMapping(
        userId: UUID,
        bookId: UUID,
    ): HardcoverBookMapping? =
        jdbi.withHandle<HardcoverBookMapping?, Exception> { h ->
            h
                .createQuery(
                    "SELECT * FROM hardcover_book_mappings WHERE user_id = ? AND book_id = ?",
                ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .map { row ->
                    HardcoverBookMapping(
                        userId = userId,
                        bookId = bookId,
                        hardcoverBookId = row.getColumn("hardcover_book_id", java.lang.Integer::class.java)?.toInt() ?: 0,
                        hardcoverEditionId = row.getColumn("hardcover_edition_id", java.lang.Integer::class.java)?.toInt(),
                        lastSyncedAt = row.getColumn("last_synced_at", String::class.java),
                    )
                }.firstOrNull()
        }

    fun saveMapping(
        userId: UUID,
        bookId: UUID,
        hardcoverBookId: Int,
        hardcoverEditionId: Int?,
    ) {
        val now = Instant.now().toString()
        val existing = getMapping(userId, bookId)
        if (existing != null) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        "UPDATE hardcover_book_mappings SET hardcover_book_id = ?, hardcover_edition_id = ?, last_synced_at = ? WHERE user_id = ? AND book_id = ?",
                    ).bind(0, hardcoverBookId)
                    .bind(1, hardcoverEditionId)
                    .bind(2, now)
                    .bind(3, userId.toString())
                    .bind(4, bookId.toString())
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        "INSERT INTO hardcover_book_mappings (user_id, book_id, hardcover_book_id, hardcover_edition_id, last_synced_at) VALUES (?, ?, ?, ?, ?)",
                    ).bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .bind(2, hardcoverBookId)
                    .bind(3, hardcoverEditionId)
                    .bind(4, now)
                    .execute()
            }
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Searches Hardcover for a book by ISBN or title+author, returns the Hardcover book ID.
     * Caches the result in the mapping table.
     */
    fun findOrMapHardcoverBook(
        userId: UUID,
        bookId: UUID,
        isbn: String?,
        title: String,
        author: String?,
    ): Int? {
        getMapping(userId, bookId)?.let { return it.hardcoverBookId }
        val key = getApiKey(userId) ?: return null
        return try {
            val hcId: Int =
                (
                    if (!isbn.isNullOrBlank()) {
                        searchByIsbn(key, isbn)
                    } else {
                        null
                            ?: searchByTitle(key, title, author)
                    }
                )
                    ?: return null
            saveMapping(userId, bookId, hcId, null)
            hcId
        } catch (e: Exception) {
            hcLogger.warn("Hardcover book lookup failed: ${e.message}")
            null
        }
    }

    private fun searchByIsbn(
        key: String,
        isbn: String,
    ): Int? {
        val result =
            graphql(
                key,
                """query(${"$"}isbn: String!) { books(where: { editions: { isbn_13: { _eq: ${"$"}isbn } } }, limit: 1) { id } }""",
                mapOf("isbn" to isbn),
            ) ?: return null
        return result
            .path("data")
            .path("books")
            .takeIf { it.isArray && it.size() > 0 }
            ?.get(0)
            ?.get("id")
            ?.asInt()
    }

    private fun searchByTitle(
        key: String,
        title: String,
        author: String?,
    ): Int? {
        val q = if (!author.isNullOrBlank()) "$title $author" else title
        val result =
            graphql(
                key,
                """query(${"$"}q: String!) { search(query: ${"$"}q, query_type: BOOK, per_page: 1) { results { hits { document { id } } } } }""",
                mapOf("q" to q),
            ) ?: return null
        return result
            .path("data")
            .path("search")
            .path("results")
            .path("hits")
            .takeIf { it.isArray && it.size() > 0 }
            ?.get(0)
            ?.path("document")
            ?.get("id")
            ?.asInt()
    }

    /**
     * Syncs a book's status to Hardcover. Returns a result indicating success.
     * [status] is one of: WANT_TO_READ, READING, FINISHED, ABANDONED.
     */
    fun syncBookStatus(
        userId: UUID,
        bookId: UUID,
        isbn: String?,
        title: String,
        author: String?,
        status: String,
    ): HardcoverSyncResult {
        val key =
            getApiKey(userId)
                ?: return HardcoverSyncResult(false, null, "No Hardcover API key configured")
        val hcId =
            findOrMapHardcoverBook(userId, bookId, isbn, title, author)
                ?: return HardcoverSyncResult(false, null, "Book not found on Hardcover")
        val hcStatus =
            when (status.uppercase()) {
                "WANT_TO_READ" -> HardcoverStatus.WANT_TO_READ
                "READING" -> HardcoverStatus.CURRENTLY_READING
                "FINISHED" -> HardcoverStatus.READ
                "ABANDONED" -> HardcoverStatus.DID_NOT_FINISH
                else -> return HardcoverSyncResult(false, hcId, "Unknown status: $status")
            }
        return try {
            graphql(
                key,
                """mutation(${"$"}bookId: Int!, ${"$"}statusId: Int!) {
                    update_user_book(object: { book_id: ${"$"}bookId, status_id: ${"$"}statusId }) { id }
                }""",
                mapOf("bookId" to hcId, "statusId" to hcStatus),
            )
            saveMapping(userId, bookId, hcId, null)
            HardcoverSyncResult(true, hcId, "Synced status $status to Hardcover")
        } catch (e: Exception) {
            hcLogger.warn("Hardcover status sync failed: ${e.message}")
            HardcoverSyncResult(false, hcId, "Sync failed: ${e.message}")
        }
    }

    /**
     * Syncs current reading progress (page) to Hardcover.
     */
    fun syncProgress(
        userId: UUID,
        bookId: UUID,
        isbn: String?,
        title: String,
        author: String?,
        currentPage: Int,
    ): HardcoverSyncResult {
        val key =
            getApiKey(userId)
                ?: return HardcoverSyncResult(false, null, "No Hardcover API key configured")
        val hcId =
            findOrMapHardcoverBook(userId, bookId, isbn, title, author)
                ?: return HardcoverSyncResult(false, null, "Book not found on Hardcover")
        return try {
            graphql(
                key,
                """mutation(${"$"}bookId: Int!, ${"$"}page: Int!) {
                    update_user_book_read(object: { book_id: ${"$"}bookId, current_page: ${"$"}page }) { id }
                }""",
                mapOf("bookId" to hcId, "page" to currentPage),
            )
            HardcoverSyncResult(true, hcId, "Progress synced: page $currentPage")
        } catch (e: Exception) {
            hcLogger.warn("Hardcover progress sync failed: ${e.message}")
            HardcoverSyncResult(false, hcId, "Sync failed: ${e.message}")
        }
    }

    // ── GraphQL helper ────────────────────────────────────────────────────────

    private fun graphql(
        apiKey: String,
        query: String,
        variables: Map<String, Any?>,
    ): com.fasterxml.jackson.databind.JsonNode? {
        val body = hcMapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
        val req =
            HttpRequest
                .newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Runary/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            hcLogger.warn("Hardcover GraphQL returned HTTP ${resp.statusCode()}")
            return null
        }
        return hcMapper.readTree(resp.body())
    }
}
