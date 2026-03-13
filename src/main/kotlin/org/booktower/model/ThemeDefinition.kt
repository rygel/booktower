package org.booktower.model

class ThemeDefinition(
    val id: String,
    val name: String,
    val type: String,
    colors: Map<String, String>,
) {
    private val _colors: Map<String, String> = colors.toMap()

    fun getColors(): Map<String, String> = _colors.toMap()
}
