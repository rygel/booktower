package org.booktower.services

/**
 * Normalizes search queries to improve matching across scripts.
 *
 * Handles:
 * - Full-width ASCII → half-width (ｓｅａｒｃｈ → search)
 * - Ideographic space → regular space
 * - Hiragana ↔ Katakana (ふ ↔ フ)
 *
 * Returns the original query plus all normalized variants so that the
 * caller can OR them together in SQL LIKE conditions.
 */
object SearchNormalizer {
    /** Unicode ranges considered CJK / Japanese script characters. */
    fun containsCjk(query: String): Boolean =
        query.any { c ->
            c in '\u3000'..'\u9FFF' ||
                // CJK Unified, Hiragana, Katakana, CJK Symbols
                c in '\uF900'..'\uFAFF' ||
                // CJK Compatibility Ideographs
                c in '\uFF00'..'\uFFEF' // Halfwidth / Fullwidth Forms
        }

    /**
     * Returns a set of search variants for the given query.
     * Always includes the trimmed original; adds normalized alternatives when they differ.
     */
    fun variants(query: String): Set<String> {
        val base = query.trim()
        val result = mutableSetOf(base)

        val halfWidth = toHalfWidth(base)
        result.add(halfWidth)

        val hiragana = katakanaToHiragana(halfWidth)
        result.add(hiragana)

        val katakana = hiraganaToKatakana(halfWidth)
        result.add(katakana)

        // Also produce hiragana/katakana variants of the half-width version
        result.add(katakanaToHiragana(base))
        result.add(hiraganaToKatakana(base))

        return result.filter { it.isNotBlank() }.toSet()
    }

    /** Full-width ASCII (U+FF01–U+FF5E) → half-width; ideographic space → space. */
    fun toHalfWidth(s: String): String =
        s
            .map { c ->
                when {
                    c == '\u3000' -> ' '
                    c in '\uFF01'..'\uFF5E' -> (c.code - 0xFEE0).toChar()
                    else -> c
                }
            }.joinToString("")

    /** Katakana (U+30A1–U+30F6) → Hiragana (U+3041–U+3096). */
    fun katakanaToHiragana(s: String): String =
        s
            .map { c ->
                if (c in '\u30A1'..'\u30F6') (c.code - 0x60).toChar() else c
            }.joinToString("")

    /** Hiragana (U+3041–U+3096) → Katakana (U+30A1–U+30F6). */
    fun hiraganaToKatakana(s: String): String =
        s
            .map { c ->
                if (c in '\u3041'..'\u3096') (c.code + 0x60).toChar() else c
            }.joinToString("")
}
