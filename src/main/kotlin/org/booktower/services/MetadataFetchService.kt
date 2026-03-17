package org.booktower.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.config.MetadataConfig
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

/** All available metadata provider keys, in default priority order. */
val METADATA_SOURCES = listOf("openlibrary", "googlebooks", "hardcover", "comicvine", "audible")

open class MetadataFetchService(private val config: MetadataConfig = MetadataConfig()) {

    protected val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Searches for metadata using all providers in priority order.
     * Pass [source] to query a specific provider only.
     * Returns null if no provider returns a match.
     */
    open fun fetchMetadata(title: String, author: String?, source: String? = null): FetchedMetadata? {
        val sources = if (source != null) listOf(source) else METADATA_SOURCES
        for (src in sources) {
            val result = when (src) {
                "openlibrary" -> fetchFromOpenLibrary(title, author)
                "googlebooks" -> fetchFromGoogleBooks(title, author)
                "hardcover" -> fetchFromHardcover(title, author)
                "comicvine" -> fetchFromComicVine(title, author)
                "audible" -> fetchFromAudible(title, author)
                else -> { logger.warn("Unknown metadata source: $src"); null }
            }
            if (result != null) return result
        }
        return null
    }

    // ── Open Library ─────────────────────────────────────────────────────────

    private fun fetchFromOpenLibrary(title: String, author: String?): FetchedMetadata? {
        return try {
            val q = buildQuery(title, author)
            val url = "https://openlibrary.org/search.json?$q&limit=1&fields=title,author_name,description,isbn,publisher,first_publish_year,cover_i,number_of_pages_median"
            val resp = get(url) ?: return null
            val root = mapper.readTree(resp)
            val docs = root.get("docs") ?: return null
            if (!docs.isArray || docs.size() == 0) return null
            val doc = docs[0]
            FetchedMetadata(
                title = doc.textOrNull("title"),
                author = doc.get("author_name")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                description = extractOpenLibraryDescription(doc),
                isbn = doc.get("isbn")?.takeIf { it.isArray && it.size() > 0 }
                    ?.firstOrNull { it.asText().length == 13 }?.asText()
                    ?: doc.get("isbn")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                publisher = doc.get("publisher")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                publishedDate = doc.textOrNull("first_publish_year"),
                openLibraryCoverId = doc.get("cover_i")?.asLong(),
                pageCount = doc.get("number_of_pages_median")?.asInt()?.takeIf { it > 0 },
                source = "openlibrary",
            )
        } catch (e: Exception) {
            logger.warn("Open Library fetch failed: ${e.message}")
            null
        }
    }

    private fun extractOpenLibraryDescription(doc: JsonNode): String? {
        val d = doc.get("description") ?: return null
        return when {
            d.isTextual -> d.asText()
            d.isObject -> d.textOrNull("value")
            else -> null
        }
    }

    // ── Google Books ─────────────────────────────────────────────────────────

    private fun fetchFromGoogleBooks(title: String, author: String?): FetchedMetadata? {
        return try {
            val q = buildGoogleQuery(title, author)
            val url = "https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=1&printType=books"
            val resp = get(url) ?: return null
            val root = mapper.readTree(resp)
            val items = root.get("items") ?: return null
            if (!items.isArray || items.size() == 0) return null
            val info = items[0].get("volumeInfo") ?: return null

            val isbn13 = info.get("industryIdentifiers")
                ?.firstOrNull { it.get("type")?.asText() == "ISBN_13" }
                ?.get("identifier")?.asText()
            val isbn10 = info.get("industryIdentifiers")
                ?.firstOrNull { it.get("type")?.asText() == "ISBN_10" }
                ?.get("identifier")?.asText()

            val thumbs = info.get("imageLinks")
            val coverUrl = thumbs?.textOrNull("thumbnail") ?: thumbs?.textOrNull("smallThumbnail")

            FetchedMetadata(
                title = info.textOrNull("title"),
                author = info.get("authors")?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.asText(),
                description = info.textOrNull("description"),
                isbn = isbn13 ?: isbn10,
                publisher = info.textOrNull("publisher"),
                publishedDate = info.textOrNull("publishedDate")?.take(4), // keep just the year
                coverUrl = coverUrl,
                pageCount = info.get("pageCount")?.asInt()?.takeIf { it > 0 },
                source = "googlebooks",
            )
        } catch (e: Exception) {
            logger.warn("Google Books fetch failed: ${e.message}")
            null
        }
    }

    // ── Hardcover ─────────────────────────────────────────────────────────────

    private fun fetchFromHardcover(title: String, author: String?): FetchedMetadata? {
        val key = config.hardcoverApiKey.takeIf { it.isNotBlank() } ?: run {
            logger.debug("Hardcover API key not configured")
            return null
        }
        return try {
            val q = buildString {
                append(title.take(200))
                if (!author.isNullOrBlank()) append(" ").append(author.take(100))
            }
            val gql = mapper.writeValueAsString(
                mapOf(
                    "query" to """query Search(${"$"}q: String!) {
                        search(query: ${"$"}q, query_type: BOOK, per_page: 1) {
                            results {
                                hits { document {
                                    title subtitle description isbn_13 isbn_10
                                    image { url }
                                    contributions { author { name } }
                                    book_series { position series { name } }
                                    language { language }
                                    pages_count
                                } }
                            }
                        }
                    }""",
                    "variables" to mapOf("q" to q),
                ),
            )
            val reqBody = HttpRequest.BodyPublishers.ofString(gql)
            val httpReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hardcover.app/v1/graphql"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $key")
                .header("User-Agent", "BookTower/1.0")
                .POST(reqBody)
                .build()
            val resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                logger.warn("Hardcover returned HTTP ${resp.statusCode()}")
                return null
            }
            val root = mapper.readTree(resp.body())
            val hits = root.path("data").path("search").path("results").path("hits")
            if (!hits.isArray || hits.size() == 0) return null
            val doc = hits[0].path("document")
            val isbn = doc.textOrNull("isbn_13") ?: doc.textOrNull("isbn_10")
            val contribution = doc.path("contributions").takeIf { it.isArray && it.size() > 0 }
                ?.get(0)?.path("author")?.textOrNull("name")
            val coverUrl = doc.path("image").textOrNull("url")
            val seriesNode = doc.path("book_series").takeIf { it.isArray && it.size() > 0 }?.get(0)
            FetchedMetadata(
                title = doc.textOrNull("title"),
                subtitle = doc.textOrNull("subtitle"),
                author = contribution,
                description = doc.textOrNull("description"),
                isbn = isbn,
                publisher = null,
                publishedDate = null,
                coverUrl = coverUrl,
                pageCount = doc.get("pages_count")?.asInt()?.takeIf { it > 0 },
                language = doc.path("language").textOrNull("language"),
                series = seriesNode?.path("series")?.textOrNull("name"),
                seriesIndex = seriesNode?.textOrNull("position")?.toDoubleOrNull(),
                source = "hardcover",
            )
        } catch (e: Exception) {
            logger.warn("Hardcover fetch failed: ${e.message}")
            null
        }
    }

    // ── ComicVine ─────────────────────────────────────────────────────────────

    private fun fetchFromComicVine(title: String, author: String?): FetchedMetadata? {
        val key = config.comicvineApiKey.takeIf { it.isNotBlank() } ?: run {
            logger.debug("ComicVine API key not configured")
            return null
        }
        return try {
            val q = URLEncoder.encode(title.take(200), "UTF-8")
            val url = "https://comicvine.gamespot.com/api/search/?api_key=$key&format=json&query=$q&resources=volume&limit=1&field_list=id,name,description,publisher,image,count_of_issues,start_year"
            val resp = get(url) ?: return null
            val root = mapper.readTree(resp)
            val results = root.get("results") ?: return null
            if (!results.isArray || results.size() == 0) return null
            val vol = results[0]
            val coverUrl = vol.path("image").textOrNull("medium_url")
                ?: vol.path("image").textOrNull("small_url")
            FetchedMetadata(
                title = vol.textOrNull("name"),
                author = null,
                description = vol.textOrNull("description")?.let { stripHtml(it) },
                isbn = null,
                publisher = vol.path("publisher").textOrNull("name"),
                publishedDate = vol.textOrNull("start_year"),
                coverUrl = coverUrl,
                pageCount = null,
                source = "comicvine",
            )
        } catch (e: Exception) {
            logger.warn("ComicVine fetch failed: ${e.message}")
            null
        }
    }

    // ── Audible (community API) ───────────────────────────────────────────────

    private fun fetchFromAudible(title: String, author: String?): FetchedMetadata? {
        return try {
            val q = buildString {
                append(URLEncoder.encode(title.take(200), "UTF-8"))
                if (!author.isNullOrBlank()) append("+").append(URLEncoder.encode(author.take(100), "UTF-8"))
            }
            val url = "https://api.audnex.us/books?query=$q"
            val resp = get(url) ?: return null
            val root = mapper.readTree(resp)
            if (!root.isArray || root.size() == 0) return null
            val book = root[0]
            val narratorList = book.get("narrators")?.takeIf { it.isArray }
                ?.map { it.textOrNull("name") }?.filterNotNull()
            val authorList = book.get("authors")?.takeIf { it.isArray }
                ?.map { it.textOrNull("name") }?.filterNotNull()
            val durationMins = book.get("runtimeLengthMin")?.asInt()?.takeIf { it > 0 }
            FetchedMetadata(
                title = book.textOrNull("title"),
                subtitle = book.textOrNull("subtitle"),
                author = authorList?.firstOrNull(),
                narrator = narratorList?.firstOrNull(),
                description = book.textOrNull("summary")?.let { stripHtml(it) },
                isbn = null,
                publisher = book.textOrNull("publisherName"),
                publishedDate = book.textOrNull("releaseDate")?.take(4),
                coverUrl = book.textOrNull("image"),
                durationSeconds = durationMins?.let { it * 60 },
                source = "audible",
            )
        } catch (e: Exception) {
            logger.warn("Audible fetch failed: ${e.message}")
            null
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").trim()

    // ── HTTP helper ───────────────────────────────────────────────────────────

    protected open fun get(url: String): String? {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "BookTower/1.0 (self-hosted book manager)")
            .GET()
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            logger.warn("HTTP ${resp.statusCode()} from $url")
            return null
        }
        return resp.body()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun buildQuery(title: String, author: String?): String {
        val sb = StringBuilder("title=").append(URLEncoder.encode(title.take(200), "UTF-8"))
        if (!author.isNullOrBlank()) sb.append("&author=").append(URLEncoder.encode(author.take(200), "UTF-8"))
        return sb.toString()
    }

    private fun buildGoogleQuery(title: String, author: String?): String {
        val q = buildString {
            append("intitle:").append(URLEncoder.encode(title.take(150), "UTF-8"))
            if (!author.isNullOrBlank()) append("+inauthor:").append(URLEncoder.encode(author.take(150), "UTF-8"))
        }
        return q
    }

    protected fun JsonNode.textOrNull(field: String): String? =
        get(field)?.takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private fun Iterable<JsonNode>.firstOrNull(predicate: (JsonNode) -> Boolean): JsonNode? =
        iterator().asSequence().firstOrNull(predicate)
}
