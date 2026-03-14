package org.booktower.model

class ThemeDefinition(
    val id: String,
    val name: String,
    val type: String,
    colors: Map<String, String>,
) {
    private val _colors: Map<String, String> = colors.toMap()

    fun getColors(): Map<String, String> = _colors.toMap()

    /** Accent color — used as the theme swatch in the selector UI. */
    val swatch: String get() = _colors["accent"] ?: "#6366f1"

    /** Border color — used as the swatch button ring. */
    val swatchBorder: String get() = _colors["border"] ?: "#374151"
}
