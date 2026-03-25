package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.runary.services.SearchNormalizer
import java.net.URLEncoder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySearchIntegrationTest : IntegrationTestBase() {
    private lateinit var token: String

    @BeforeEach
    fun setup() {
        token = registerAndGetToken("fuzzy")
    }

    // ── SearchNormalizer unit-level checks ────────────────────────────────────

    @Test
    fun `full-width ASCII normalizes to half-width`() {
        val v = SearchNormalizer.variants("ｈｏｂｂｉｔ")
        assertTrue(v.contains("hobbit"), "Expected half-width 'hobbit' in $v")
    }

    @Test
    fun `katakana query produces hiragana variant`() {
        val v = SearchNormalizer.variants("ハリー")
        assertTrue(v.contains("はりー"), "Expected hiragana 'はりー' in $v")
    }

    @Test
    fun `hiragana query produces katakana variant`() {
        val v = SearchNormalizer.variants("はりー")
        assertTrue(v.contains("ハリー"), "Expected katakana 'ハリー' in $v")
    }

    @Test
    fun `ASCII query produces only itself`() {
        val v = SearchNormalizer.variants("hobbit")
        assertEquals(setOf("hobbit"), v)
    }

    // ── End-to-end search via the HTTP API ────────────────────────────────────

    private fun addBook(
        title: String,
        author: String = "Author",
    ): String {
        val libId = createLibrary(token)
        return createBook(token, libId, title)
    }

    private fun search(q: String): String {
        val resp =
            app(
                Request(Method.GET, "/api/search?q=${URLEncoder.encode(q, "UTF-8")}")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(200, resp.status.code)
        return resp.bodyString()
    }

    @Test
    fun `exact ASCII search returns book`() {
        addBook("The Hobbit")
        val body = search("Hobbit")
        assertTrue(body.contains("The Hobbit"), "Expected book in results")
    }

    @Test
    fun `katakana title found by hiragana query`() {
        addBook("ハリーポッター") // stored in Katakana
        val body = search("はりーぽったー") // search in Hiragana
        assertTrue(body.contains("ハリーポッター"), "Hiragana query should match Katakana title")
    }

    @Test
    fun `hiragana title found by katakana query`() {
        addBook("ほびっと") // stored in Hiragana
        val body = search("ホビット") // search in Katakana
        assertTrue(body.contains("ほびっと"), "Katakana query should match Hiragana title")
    }

    @Test
    fun `full-width query matches half-width stored title`() {
        addBook("search test")
        val body = search("ｓｅａｒｃｈ") // full-width ASCII
        assertTrue(body.contains("search test"), "Full-width query should match half-width title")
    }

    @Test
    fun `CJK substring search works`() {
        addBook("東京タワー物語") // Tokyo Tower Story
        val body = search("タワー") // Tower
        assertTrue(body.contains("東京タワー物語"), "CJK substring search should match")
    }
}
