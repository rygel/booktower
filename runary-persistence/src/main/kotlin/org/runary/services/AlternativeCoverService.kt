package org.runary.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val altCoverLogger = LoggerFactory.getLogger("runary.AlternativeCoverService")
private val altCoverMapper = ObjectMapper()

data class CoverCandidate(
    val url: String,
    val source: String,
    val width: Int? = null,
    val height: Int? = null,
)

open class AlternativeCoverService {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    /**
     * Returns a deduplicated list of cover image URLs from all available sources.
     * At most 3 candidates per source; never throws.
     */
    open fun fetchCandidates(
        title: String,
        author: String?,
        isbn: String?,
    ): List<CoverCandidate> {
        val results = mutableListOf<CoverCandidate>()
        results += fromOpenLibrary(title, author, isbn)
        results += fromGoogleBooks(title, author)
        return results.distinctBy { it.url }
    }

    /**
     * Downloads bytes from [url] and returns them, or null on failure.
     * Used by the "apply URL" endpoint to fetch and save the cover.
     */
    open fun downloadBytes(url: String): ByteArray? {
        return try {
            val req =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Runary/1.0 (self-hosted book manager)")
                    .GET()
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() != 200) {
                altCoverLogger.warn("HTTP ${resp.statusCode()} downloading cover from $url")
                return null
            }
            resp.body()
        } catch (e: Exception) {
            altCoverLogger.warn("Failed to download cover from $url: ${e.message}")
            null
        }
    }

    // ── OpenLibrary ───────────────────────────────────────────────────────────

    private fun fromOpenLibrary(
        title: String,
        author: String?,
        isbn: String?,
    ): List<CoverCandidate> {
        return try {
            val candidates = mutableListOf<CoverCandidate>()

            // ISBN cover is the most accurate when available
            if (!isbn.isNullOrBlank()) {
                val cleaned = isbn.replace("-", "")
                candidates +=
                    CoverCandidate(
                        url = "https://covers.openlibrary.org/b/isbn/$cleaned-L.jpg",
                        source = "openlibrary",
                    )
            }

            // Search by title/author and use cover_i
            val q =
                buildString {
                    append("title=").append(URLEncoder.encode(title.take(200), "UTF-8"))
                    if (!author.isNullOrBlank()) append("&author=").append(URLEncoder.encode(author.take(200), "UTF-8"))
                }
            val resp = get("https://openlibrary.org/search.json?$q&limit=3&fields=cover_i") ?: return candidates
            val root = altCoverMapper.readTree(resp)
            val docs = root.get("docs") ?: return candidates
            if (docs.isArray) {
                for (doc in docs) {
                    val coverId = doc.get("cover_i")?.asLong()?.takeIf { it > 0 } ?: continue
                    candidates +=
                        CoverCandidate(
                            url = "https://covers.openlibrary.org/b/id/$coverId-L.jpg",
                            source = "openlibrary",
                        )
                }
            }
            candidates
        } catch (e: Exception) {
            altCoverLogger.warn("OpenLibrary cover search failed: ${e.message}")
            emptyList()
        }
    }

    // ── Google Books ──────────────────────────────────────────────────────────

    private fun fromGoogleBooks(
        title: String,
        author: String?,
    ): List<CoverCandidate> {
        return try {
            val q =
                buildString {
                    append("intitle:").append(URLEncoder.encode(title.take(150), "UTF-8"))
                    if (!author.isNullOrBlank()) append("+inauthor:").append(URLEncoder.encode(author.take(150), "UTF-8"))
                }
            val resp = get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=3&printType=books") ?: return emptyList()
            val root = altCoverMapper.readTree(resp)
            val items = root.get("items") ?: return emptyList()
            val results = mutableListOf<CoverCandidate>()
            if (items.isArray) {
                for (item in items) {
                    val info = item.get("volumeInfo") ?: continue
                    val links = info.get("imageLinks") ?: continue
                    val url =
                        links.get("large")?.asText()
                            ?: links.get("medium")?.asText()
                            ?: links.get("thumbnail")?.asText()
                            ?: continue
                    results += CoverCandidate(url = url.replace("http://", "https://"), source = "googlebooks")
                }
            }
            results
        } catch (e: Exception) {
            altCoverLogger.warn("Google Books cover search failed: ${e.message}")
            emptyList()
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    protected open fun get(url: String): String? {
        return try {
            val req =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Runary/1.0 (self-hosted book manager)")
                    .GET()
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                altCoverLogger.warn("HTTP ${resp.statusCode()} from $url")
                return null
            }
            resp.body()
        } catch (e: Exception) {
            altCoverLogger.warn("HTTP request failed for $url: ${e.message}")
            null
        }
    }
}
