package org.runary.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = LoggerFactory.getLogger("runary.AuthorMetadataService")
private val authorMapper = ObjectMapper()

data class AuthorInfo(
    val name: String,
    val bio: String? = null,
    val photoUrl: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val workCount: Int? = null,
    val topWork: String? = null,
)

open class AuthorMetadataService {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    /**
     * Searches OpenLibrary for an author by name and returns bio + photo.
     * Returns null if the author is not found or the request fails.
     */
    open fun fetch(name: String): AuthorInfo? {
        return try {
            val q = URLEncoder.encode(name.take(200), "UTF-8")
            val searchResp = get("https://openlibrary.org/search/authors.json?q=$q&limit=1") ?: return null
            val searchRoot = authorMapper.readTree(searchResp)
            val docs = searchRoot.get("docs") ?: return null
            if (!docs.isArray || docs.size() == 0) return null
            val doc = docs[0]

            val key = doc.get("key")?.asText() ?: return null // e.g. "OL26320A"
            val authorName = doc.get("name")?.asText() ?: name
            val workCount = doc.get("work_count")?.asInt()
            val topWork = doc.get("top_work")?.asText()?.takeIf { it.isNotBlank() }
            val birthDate = doc.get("birth_date")?.asText()?.takeIf { it.isNotBlank() }
            val deathDate = doc.get("death_date")?.asText()?.takeIf { it.isNotBlank() }

            // Photo URL — covers.openlibrary.org uses the bare key without /authors/ prefix
            val photoUrl = "https://covers.openlibrary.org/a/olid/$key-L.jpg"

            // Fetch the full author record for bio
            val bio = fetchBio(key)

            AuthorInfo(
                name = authorName,
                bio = bio,
                photoUrl = photoUrl,
                birthDate = birthDate,
                deathDate = deathDate,
                workCount = workCount,
                topWork = topWork,
            )
        } catch (e: Exception) {
            logger.warn("Author metadata fetch failed for '$name': ${e.message}")
            null
        }
    }

    private fun fetchBio(key: String): String? {
        return try {
            val resp = get("https://openlibrary.org/authors/$key.json") ?: return null
            val root = authorMapper.readTree(resp)
            val bioNode = root.get("bio") ?: return null
            when {
                bioNode.isTextual -> bioNode.asText().takeIf { it.isNotBlank() }
                bioNode.isObject -> bioNode.get("value")?.asText()?.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (e: Exception) {
            logger.debug("Could not fetch bio for author key $key: ${e.message}")
            null
        }
    }

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
                logger.warn("HTTP ${resp.statusCode()} from $url")
                return null
            }
            resp.body()
        } catch (e: Exception) {
            logger.warn("HTTP request failed for $url: ${e.message}")
            null
        }
    }
}
