package org.booktower.weblate

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Properties

class WeblateBridge(
    private val weblateUrl: String,
    private val apiToken: String,
    private val component: String,
    private val translationsDir: File,
) {
    private val logger = LoggerFactory.getLogger(WeblateBridge::class.java)
    private val client = HttpClient.newHttpClient()

    fun pullTranslations(languages: List<String> = listOf("en", "fr")): Map<String, Int> {
        val results = mutableMapOf<String, Int>()

        for (lang in languages) {
            try {
                val props = fetchTranslation(lang)
                if (props != null) {
                    val file = getTranslationFile(lang)
                    saveProperties(props, file)
                    results[lang] = props.size
                    logger.info("Pulled ${props.size} translations for language: $lang")
                }
            } catch (e: Exception) {
                logger.error("Failed to pull translations for $lang: ${e.message}")
            }
        }

        return results
    }

    fun pushTranslations(languages: List<String> = listOf("en", "fr")): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        for (lang in languages) {
            try {
                val file = getTranslationFile(lang)
                if (file.exists()) {
                    val props = loadProperties(file)
                    val success = pushTranslation(lang, props)
                    results[lang] = success
                    logger.info("Push translations for $lang: ${if (success) "success" else "failed"}")
                }
            } catch (e: Exception) {
                logger.error("Failed to push translations for $lang: ${e.message}")
                results[lang] = false
            }
        }

        return results
    }

    private fun fetchTranslation(language: String): Properties? {
        val url = "$weblateUrl/api/translations/$component/messages/$language/file/"

        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token $apiToken")
                .GET()
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        return if (response.statusCode() == 200) {
            val props = Properties()
            props.load(response.body().reader())
            props
        } else {
            logger.warn("Failed to fetch translations: HTTP ${response.statusCode()}")
            null
        }
    }

    private fun pushTranslation(
        language: String,
        properties: Properties,
    ): Boolean {
        val url = "$weblateUrl/api/translations/$component/messages/$language/file/"

        val bodyBuilder = StringBuilder()
        properties.forEach { key, value ->
            if (bodyBuilder.isNotEmpty()) bodyBuilder.append("\n")
            bodyBuilder.append("$key=$value")
        }

        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token $apiToken")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() == 200
    }

    private fun getTranslationFile(language: String): File {
        val filename =
            if (language == "en" || language == "en-US") {
                "messages.properties"
            } else {
                "messages_$language.properties"
            }
        return File(translationsDir, filename)
    }

    private fun loadProperties(file: File): Properties {
        val properties = Properties()
        FileInputStream(file).use { fis ->
            properties.load(fis)
        }
        return properties
    }

    private fun saveProperties(
        properties: Properties,
        file: File,
    ) {
        FileOutputStream(file).use { fos ->
            properties.store(fos, "BookTower translations - synced from Weblate")
        }
    }

    fun getTranslationStatus(): WeblateStatus? {
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
                parseStatus(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to get translation status: ${e.message}")
            null
        }
    }

    private fun parseStatus(json: String): WeblateStatus {
        val translated = json.extract("\"translated_words\":(\\d+)")?.toIntOrNull() ?: 0
        val total = json.extract("\"total_words\":(\\d+)")?.toIntOrNull() ?: 0
        val fuzzy = json.extract("\"fuzzy_words\":(\\d+)")?.toIntOrNull() ?: 0

        return WeblateStatus(
            translatedWords = translated,
            totalWords = total,
            fuzzyWords = fuzzy,
        )
    }

    private fun String.extract(pattern: String): String? {
        return Regex(pattern).find(this)?.groupValues?.getOrNull(1)
    }

    data class WeblateStatus(
        val translatedWords: Int,
        val totalWords: Int,
        val fuzzyWords: Int,
    ) {
        val progressPercent: Double
            get() = if (totalWords > 0) (translatedWords.toDouble() / totalWords) * 100 else 0.0
    }

    companion object {
        fun create(
            weblateUrl: String?,
            apiToken: String?,
            component: String?,
            translationsDir: String,
        ): WeblateBridge? {
            return if (!weblateUrl.isNullOrBlank() && !apiToken.isNullOrBlank() && !component.isNullOrBlank()) {
                WeblateBridge(weblateUrl, apiToken, component, File(translationsDir))
            } else {
                null
            }
        }
    }
}
