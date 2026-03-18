package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

/**
 * Age rating levels, ordered from most permissive (index 0) to most restrictive.
 * A user restricted to "PG-13" can see G, PG, and PG-13 but not R or NC-17.
 */
val AGE_RATING_ORDER = listOf("G", "PG", "PG-13", "R", "NC-17", "X")

data class ContentRestrictions(
    val userId: String,
    val maxAgeRating: String?,
    val blockedTags: List<String>,
)

class ContentRestrictionsService(
    private val jdbi: Jdbi,
    private val userSettingsService: UserSettingsService,
) {
    companion object {
        const val KEY_MAX_AGE_RATING = "content_max_age_rating"
        const val KEY_BLOCKED_TAGS = "content_blocked_tags"
    }

    fun get(userId: UUID): ContentRestrictions {
        val maxRating = userSettingsService.get(userId, KEY_MAX_AGE_RATING)
        val blockedTagsRaw = userSettingsService.get(userId, KEY_BLOCKED_TAGS)
        val blockedTags = blockedTagsRaw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return ContentRestrictions(
            userId = userId.toString(),
            maxAgeRating = maxRating?.takeIf { it.isNotBlank() },
            blockedTags = blockedTags,
        )
    }

    fun setMaxAgeRating(
        userId: UUID,
        maxRating: String?,
    ) {
        if (maxRating != null) {
            require(maxRating in AGE_RATING_ORDER) { "Unknown age rating: $maxRating. Valid: ${AGE_RATING_ORDER.joinToString()}" }
        }
        userSettingsService.set(userId, KEY_MAX_AGE_RATING, maxRating ?: "")
    }

    fun setBlockedTags(
        userId: UUID,
        tags: List<String>,
    ) {
        userSettingsService.set(userId, KEY_BLOCKED_TAGS, tags.joinToString(","))
    }

    /**
     * Returns true if a book with [bookAgeRating] is allowed for [userId].
     * Books without an age rating are always permitted (null = unrated = allowed).
     */
    fun isAllowed(
        userId: UUID,
        bookAgeRating: String?,
    ): Boolean {
        if (bookAgeRating == null) return true
        val restrictions = get(userId)
        val maxRating = restrictions.maxAgeRating ?: return true // no restriction
        val maxIdx = AGE_RATING_ORDER.indexOf(maxRating)
        val bookIdx = AGE_RATING_ORDER.indexOf(bookAgeRating)
        if (bookIdx < 0) return true // unknown rating = allow
        return bookIdx <= maxIdx
    }
}
