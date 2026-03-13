package org.booktower.weblate

import org.booktower.config.WeblateConfig

object WeblateSyncCommand {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = loadConfig()
        
        if (config.url.isBlank() || config.apiToken.isBlank()) {
            println("Weblate not configured. Set weblate.url and weblate.api-token in application.conf")
            println("Usage: mvn compile exec:java -Dexec.mainClass=\"org.booktower.weblate.WeblateSyncCommand\" -Dexec.args=\"pull\"")
            return
        }

        val bridge = WeblateBridge(
            weblateUrl = config.url,
            apiToken = config.apiToken,
            component = config.component,
            translationsDir = getTranslationsDir()
        )

        when (args.firstOrNull()) {
            "pull" -> {
                println("Pulling translations from Weblate...")
                val results = bridge.pullTranslations()
                results.forEach { (lang, count) ->
                    println("  $lang: $count translations")
                }
            }
            "push" -> {
                println("Pushing translations to Weblate...")
                val results = bridge.pushTranslations()
                results.forEach { (lang, success) ->
                    println("  $lang: ${if (success) "success" else "failed"}")
                }
            }
            "status" -> {
                println("Fetching translation status...")
                bridge.getTranslationStatus()?.let { status ->
                    println("  Translated: ${status.translatedWords} words")
                    println("  Fuzzy: ${status.fuzzyWords} words")
                    println("  Total: ${status.totalWords} words")
                    println("  Progress: ${String.format("%.1f", status.progressPercent)}%")
                } ?: println("  Failed to fetch status")
            }
            else -> {
                println("Usage: WeblateSyncCommand <command>")
                println("Commands:")
                println("  pull   - Pull translations from Weblate")
                println("  push   - Push local translations to Weblate")
                println("  status - Show translation status")
            }
        }
    }

    private fun loadConfig(): WeblateConfig {
        return try {
            val config = com.typesafe.config.ConfigFactory.load()
            val app = config.getConfig("app")
            WeblateConfig.load(app.getConfig("weblate"))
        } catch (e: Exception) {
            WeblateConfig("", "", "", false)
        }
    }

    private fun getTranslationsDir(): java.io.File {
        val resourcesDir = java.io.File("src/main/resources")
        if (!resourcesDir.exists()) {
            java.io.File("target/classes").also { it.mkdirs() }
        }
        return resourcesDir
    }
}
