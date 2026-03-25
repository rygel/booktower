package org.runary.models

data class ErrorResponse(
    val error: String,
    val message: String,
)

data class SuccessResponse(
    val message: String,
)

data class Language(
    val code: String,
    val name: String,
)

data class ThemePreference(
    val theme: String,
)

data class LanguagePreference(
    val lang: String,
)
