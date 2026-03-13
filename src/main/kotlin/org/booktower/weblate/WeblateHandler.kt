package org.booktower.weblate

import org.booktower.config.WeblateConfig

class WeblateHandler(
    private val integration: WeblateIntegration
) {
    fun pull(req: org.http4k.core.Request): org.http4k.core.Response {
        if (!integration.isEnabled()) {
            return org.http4k.core.Response(org.http4k.core.Status.SERVICE_UNAVAILABLE)
                .body("Weblate integration not enabled")
        }

        val lang = req.query("lang")?.split(",") ?: listOf("en", "fr")
        val results = integration.pullTranslations(lang)
        
        return org.http4k.core.Response(org.http4k.core.Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"status": "success", "results": $results}""")
    }

    fun push(req: org.http4k.core.Request): org.http4k.core.Response {
        if (!integration.isEnabled()) {
            return org.http4k.core.Response(org.http4k.core.Status.SERVICE_UNAVAILABLE)
                .body("Weblate integration not enabled")
        }

        val lang = req.query("lang")?.split(",") ?: listOf("en", "fr")
        val results = integration.pushTranslations(lang)
        
        return org.http4k.core.Response(org.http4k.core.Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"status": "success", "results": $results}""")
    }

    fun status(req: org.http4k.core.Request): org.http4k.core.Response {
        if (!integration.isEnabled()) {
            return org.http4k.core.Response(org.http4k.core.Status.SERVICE_UNAVAILABLE)
                .body("Weblate integration not enabled")
        }

        val status = integration.getStatus()
        
        return if (status != null) {
            org.http4k.core.Response(org.http4k.core.Status.OK)
                .header("Content-Type", "application/json")
                .body("""{
                    "translated": ${status.translatedWords},
                    "total": ${status.totalWords},
                    "fuzzy": ${status.fuzzyWords},
                    "progress": ${status.progressPercent}
                }""")
        } else {
            org.http4k.core.Response(org.http4k.core.Status.INTERNAL_SERVER_ERROR)
                .body("Failed to fetch status")
        }
    }
}
