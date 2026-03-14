package org.booktower.weblate

import org.booktower.config.Json
import org.booktower.config.WeblateConfig
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class WeblateHandler(private val config: WeblateConfig) {
    private val bridge: WeblateBridge? by lazy {
        if (config.enabled && config.url.isNotBlank() && config.apiToken.isNotBlank()) {
            WeblateBridge(config.url, config.apiToken, config.component, java.io.File("src/main/resources"))
        } else {
            null
        }
    }

    fun pull(req: Request): Response {
        val b = bridge ?: return unavailable()
        val lang = req.query("lang")?.split(",") ?: listOf("en", "fr")
        val results = b.pullTranslations(lang)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("status" to "success", "results" to results)))
    }

    fun push(req: Request): Response {
        val b = bridge ?: return unavailable()
        val lang = req.query("lang")?.split(",") ?: listOf("en", "fr")
        val results = b.pushTranslations(lang)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("status" to "success", "results" to results)))
    }

    fun status(req: Request): Response {
        val b = bridge ?: return unavailable()
        val status = b.getTranslationStatus()
            ?: return Response(Status.INTERNAL_SERVER_ERROR).body("Failed to fetch status")

        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper.writeValueAsString(
                    mapOf(
                        "translated" to status.translatedWords,
                        "total" to status.totalWords,
                        "fuzzy" to status.fuzzyWords,
                        "progress" to status.progressPercent,
                    ),
                ),
            )
    }

    private fun unavailable() = Response(Status.SERVICE_UNAVAILABLE)
        .header("Content-Type", "application/json")
        .body(Json.mapper.writeValueAsString(mapOf("error" to "Weblate integration not enabled")))
}
