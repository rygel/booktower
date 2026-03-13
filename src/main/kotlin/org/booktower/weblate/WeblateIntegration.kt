package org.booktower.weblate

import org.booktower.config.WeblateConfig

class WeblateIntegration(
    private val config: WeblateConfig,
    private val translationsDir: String
) {
    private val bridge: WeblateBridge? by lazy {
        if (config.enabled && config.url.isNotBlank() && config.apiToken.isNotBlank()) {
            WeblateBridge(config.url, config.apiToken, config.component, java.io.File(translationsDir))
        } else {
            null
        }
    }

    fun isEnabled(): Boolean = bridge != null

    fun pullTranslations(languages: List<String> = listOf("en", "fr")): Map<String, Int> {
        return bridge?.pullTranslations(languages) ?: emptyMap()
    }

    fun pushTranslations(languages: List<String> = listOf("en", "fr")): Map<String, Boolean> {
        return bridge?.pushTranslations(languages) ?: emptyMap()
    }

    fun getStatus(): WeblateBridge.WeblateStatus? {
        return bridge?.getTranslationStatus()
    }
}
