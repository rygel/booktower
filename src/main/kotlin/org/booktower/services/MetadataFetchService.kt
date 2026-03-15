package org.booktower.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.models.FetchedMetadata
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = LoggerFactory.getLogger("booktower.MetadataFetchService")
private val mapper = ObjectMapper()

open class MetadataFetchService {

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Searches Open Library for a book by title and/or author and returns the best match.
     * Returns null on network error or no results.
     */
    open fun fetchMetadata(title: String, author: String?): FetchedMetadata? {
        return try {
            val q = buildQuery(title, author)
            val url = "https://openlibrary.org/search.json?$q&limit=1&fields=title,author_name,description,isbn,publisher,first_publish_year,cover_i"
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "BookTower/1.0 (self-hosted book manager)")
                .GET()
                .build()

            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                logger.warn("Open Library returned ${resp.statusCode()} for query: $q")
                return null
            }

            val root = mapper.readTree(resp.body())
            val docs = root.get("docs") ?: return null
            if (!docs.isArray || docs.size() == 0) return null

            val doc = docs[0]
            FetchedMetadata(
                title = doc.textOrNull("title"),
                author = doc.get("author_name")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                description = fetchDescription(doc),
                isbn = doc.get("isbn")?.takeIf { it.isArray && it.size() > 0 }
                    ?.firstOrNull { it.asText().length == 13 }?.asText()
                    ?: doc.get("isbn")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                publisher = doc.get("publisher")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                publishedDate = doc.textOrNull("first_publish_year"),
                openLibraryCoverId = doc.get("cover_i")?.asLong(),
            )
        } catch (e: Exception) {
            logger.warn("Metadata fetch failed: ${e.message}")
            null
        }
    }

    /** Fetch richer description from the works API if available. */
    private fun fetchDescription(doc: JsonNode): String? {
        // description is sometimes in a sub-object: {"value": "..."}
        val inSearch = doc.get("description")
        if (inSearch != null) {
            return when {
                inSearch.isTextual -> inSearch.asText()
                inSearch.isObject -> inSearch.textOrNull("value")
                else -> null
            }
        }
        return null
    }

    private fun buildQuery(title: String, author: String?): String {
        val sb = StringBuilder("title=").append(URLEncoder.encode(title.take(200), "UTF-8"))
        if (!author.isNullOrBlank()) {
            sb.append("&author=").append(URLEncoder.encode(author.take(200), "UTF-8"))
        }
        return sb.toString()
    }

    private fun JsonNode.textOrNull(field: String): String? =
        get(field)?.takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private fun Iterable<JsonNode>.firstOrNull(predicate: (JsonNode) -> Boolean): JsonNode? =
        iterator().asSequence().firstOrNull(predicate)
}
