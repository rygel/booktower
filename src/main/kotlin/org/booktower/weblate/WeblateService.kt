package org.booktower.weblate

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Properties

class WeblateService(
    private val weblateUrl: String?,
    private val apiToken: String?,
) {
    private val logger = LoggerFactory.getLogger(WeblateService::class.java)
    private val client = HttpClient.newHttpClient()

    fun fetchTranslations(
        component: String,
        language: String,
    ): Properties? {
        if (weblateUrl.isNullOrBlank() || apiToken.isNullOrBlank()) {
            logger.debug("Weblate not configured, skipping translation fetch")
            return null
        }

        return try {
            val url = "$weblateUrl/api/translations/$component/messages/$language/file/"
            logger.info("Fetching translations from Weblate: $url")

            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token $apiToken")
                    .GET()
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val props = Properties()
                props.load(response.body().reader())
                logger.info("Successfully fetched ${props.size} translations for $language")
                props
            } else {
                logger.warn("Failed to fetch translations: ${response.statusCode()}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error fetching translations from Weblate: ${e.message}")
            null
        }
    }

    fun pushTranslation(
        component: String,
        language: String,
        properties: Properties,
    ): Boolean {
        if (weblateUrl.isNullOrBlank() || apiToken.isNullOrBlank()) {
            logger.debug("Weblate not configured, skipping translation push")
            return false
        }

        return try {
            val url = "$weblateUrl/api/translations/$component/messages/$language/file/"
            val body =
                Properties().apply {
                    putAll(properties)
                }.toString()

            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token $apiToken")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            logger.error("Error pushing translations to Weblate: ${e.message}")
            false
        }
    }

    fun getTranslationStatus(component: String): TranslationStatus? {
        if (weblateUrl.isNullOrBlank() || apiToken.isNullOrBlank()) {
            return null
        }

        return try {
            val url = "$weblateUrl/api/components/$component/"
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token $apiToken")
                    .GET()
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                parseTranslationStatus(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting translation status: ${e.message}")
            null
        }
    }

    private fun parseTranslationStatus(json: String): TranslationStatus {
        // Simple JSON parsing - in production use Jackson
        val translated = json.extract("\"translated\":(\\d+)")?.toIntOrNull() ?: 0
        val total = json.extract("\"total\":(\\d+)")?.toIntOrNull() ?: 0
        val fuzzy = json.extract("\"fuzzy\":(\\d+)")?.toIntOrNull() ?: 0

        return TranslationStatus(translated, total, fuzzy)
    }

    private fun String.extract(pattern: String): String? {
        return Regex(pattern).find(this)?.groupValues?.getOrNull(1)
    }

    data class TranslationStatus(
        val translatedWords: Int,
        val totalWords: Int,
        val fuzzyCount: Int,
    ) {
        val percentage: Double
            get() = if (totalWords > 0) (translatedWords.toDouble() / totalWords) * 100 else 0.0
    }

    companion object {
        fun create(
            weblateUrl: String?,
            apiToken: String?,
        ): WeblateService? {
            return if (!weblateUrl.isNullOrBlank() && !apiToken.isNullOrBlank()) {
                WeblateService(weblateUrl, apiToken)
            } else {
                null
            }
        }
    }
}
