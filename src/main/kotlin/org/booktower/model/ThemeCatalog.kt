package org.booktower.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object ThemeCatalog {
    private val objectMapper = jacksonObjectMapper()

    private val themes: List<ThemeDefinition> by lazy {
        val resourceStream =
            requireNotNull(ThemeCatalog::class.java.classLoader.getResourceAsStream("themes.json")) {
                "Unable to load themes.json from the classpath."
            }

        resourceStream.use { objectMapper.readValue<List<ThemeDefinition>>(it) }
    }

    fun allThemes(): List<ThemeDefinition> = themes

    fun findTheme(themeId: String): ThemeDefinition = themes.firstOrNull { it.id == themeId } ?: themes.first { it.id == "dark" }

    fun toCssVariables(themeId: String): String {
        val theme = findTheme(themeId)

        return buildString {
            append(":root {")
            theme.getColors().forEach { (key, value) ->
                append("--color-")
                append(key)
                append(": ")
                append(value)
                append(";")
            }
            append("} body { color-scheme: ")
            append(if (theme.type == "light") "light" else "dark")
            append("; }")
        }
    }
}
