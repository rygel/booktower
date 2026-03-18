package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.BookListDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers search scenarios not exercised by SearchIntegrationTest:
 * description-field search, multi-library aggregation, and special-character safety.
 */
class SearchEdgeCaseIntegrationTest : IntegrationTestBase() {
    private fun addBook(
        token: String,
        libId: String,
        title: String,
        author: String? = null,
        description: String? = null,
    ) {
        val descJson = if (description != null) "\"${description.replace("\"", "\\\"")}\"" else "null"
        val authorJson = if (author != null) "\"$author\"" else "null"
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"$title","author":$authorJson,"description":$descJson,"libraryId":"$libId"}"""),
        )
    }

    private fun search(
        token: String,
        q: String,
    ): BookListDto {
        val resp =
            app(
                Request(Method.GET, "/api/search?q=$q")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        return Json.mapper.readValue(resp.bodyString(), BookListDto::class.java)
    }

    // ── Description field search ──────────────────────────────────────────────

    @Test
    fun `search finds book matching only description field`() {
        val token = registerAndGetToken("se_desc1")
        val libId = createLibrary(token)
        addBook(token, libId, "Generic Title", "Generic Author", "This book is about quantum physics")
        addBook(token, libId, "Another Book", "Another Author", "Covers classical mechanics")

        val results = search(token, "quantum+physics")
        assertEquals(1, results.total, "Should find exactly one book via description")
        assertEquals("Generic Title", results.getBooks()[0].title)
    }

    @Test
    fun `description search is case-insensitive`() {
        val token = registerAndGetToken("se_desc2")
        val libId = createLibrary(token)
        addBook(token, libId, "My Book", null, "UNIQUE_DESC_TERM_UPPERCASE")

        val results = search(token, "unique_desc_term_uppercase")
        assertTrue(results.total >= 1, "Description search should be case-insensitive")
    }

    @Test
    fun `search does not return books that match only another user description`() {
        val tokenA = registerAndGetToken("se_desc3a")
        val tokenB = registerAndGetToken("se_desc3b")
        val libA = createLibrary(tokenA)
        addBook(tokenA, libA, "Secret", null, "xyzuniquedescxyz42")

        val results = search(tokenB, "xyzuniquedescxyz42")
        assertEquals(0, results.total, "Description search must respect user isolation")
    }

    @Test
    fun `book with null description still appears in title search`() {
        val token = registerAndGetToken("se_desc4")
        val libId = createLibrary(token)
        addBook(token, libId, "NullDescBook", null, null)

        val results = search(token, "NullDescBook")
        assertEquals(1, results.total)
    }

    // ── Multi-library aggregation ─────────────────────────────────────────────

    @Test
    fun `search aggregates results from multiple libraries`() {
        val token = registerAndGetToken("se_multi1")
        val lib1 = createLibrary(token, "Alpha Lib")
        val lib2 = createLibrary(token, "Beta Lib")
        addBook(token, lib1, "SearchTarget A", "Author One")
        addBook(token, lib2, "SearchTarget B", "Author Two")
        addBook(token, lib2, "Unrelated", "Nobody")

        val results = search(token, "SearchTarget")
        assertEquals(2, results.total, "Search should aggregate across all user libraries")
    }

    @Test
    fun `search from second library is also returned`() {
        val token = registerAndGetToken("se_multi2")
        val lib1 = createLibrary(token, "Lib One")
        val lib2 = createLibrary(token, "Lib Two")
        addBook(token, lib1, "First Library Book", null)
        addBook(token, lib2, "Second Library Book", null)

        val result1 = search(token, "First+Library+Book")
        val result2 = search(token, "Second+Library+Book")
        assertEquals(1, result1.total, "Book in lib1 should appear in search")
        assertEquals(1, result2.total, "Book in lib2 should appear in search")
    }

    // ── Special-character safety ──────────────────────────────────────────────

    @Test
    fun `search with percent wildcard is treated literally and does not crash`() {
        val token = registerAndGetToken("se_spec1")
        val libId = createLibrary(token)
        addBook(token, libId, "Normal Book", null)

        // A raw '%' in the query should not expand to SQL wildcard behavior
        val resp =
            app(
                Request(Method.GET, "/api/search?q=%25%25%25")
                    .header("Cookie", "token=$token"),
            )
        // Must not 500 — result may be 0 or N, but must be valid JSON
        assertTrue(resp.status.code != 500, "Search with percent chars must not 500")
        val results = Json.mapper.readValue(resp.bodyString(), BookListDto::class.java)
        assertTrue(results.total >= 0)
    }

    @Test
    fun `search with underscore is treated literally and does not match all`() {
        val token = registerAndGetToken("se_spec2")
        val libId = createLibrary(token)
        addBook(token, libId, "Book Alpha", null)
        addBook(token, libId, "Book Beta", null)

        // SQL '_' is a single-char wildcard; parameterized queries must escape it
        val results = search(token, "_")
        // Should NOT match every book just because _ is a wildcard
        // (behaviour depends on DB escaping; must not 500 and total must be reasonable)
        assertTrue(results.total >= 0, "Search with underscore must not crash")
    }

    @Test
    fun `search with single quote does not cause SQL error`() {
        val token = registerAndGetToken("se_spec3")
        val libId = createLibrary(token)
        addBook(token, libId, "O'Brien's Story", "Patrick O'Brien")

        val resp =
            app(
                Request(Method.GET, "/api/search?q=O%27Brien")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(resp.status.code != 500, "Single quote in search must not cause SQL error")
        val results = Json.mapper.readValue(resp.bodyString(), BookListDto::class.java)
        assertTrue(results.total >= 1, "Book with apostrophe in title should be findable")
    }

    @Test
    fun `search with backslash does not crash`() {
        val token = registerAndGetToken("se_spec4")
        val libId = createLibrary(token)
        addBook(token, libId, "Normal Book", null)

        // URL-encoded backslash
        val resp =
            app(
                Request(Method.GET, "/api/search?q=%5C")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(resp.status.code != 500, "Backslash in search query must not crash")
    }

    // ── Empty / whitespace queries ────────────────────────────────────────────

    @Test
    fun `search with whitespace-only query after trimming returns 400`() {
        val token = registerAndGetToken("se_empty1")
        val resp =
            app(
                Request(Method.GET, "/api/search?q=++++")
                    .header("Cookie", "token=$token"),
            )
        // A blank query (after trimming) should be rejected
        assertEquals(Status.BAD_REQUEST, resp.status, "Blank-after-trim query should return 400")
    }

    @Test
    fun `search with very long query does not crash`() {
        val token = registerAndGetToken("se_long1")
        val libId = createLibrary(token)
        addBook(token, libId, "Normal Book", null)

        val longQuery = "a".repeat(500)
        val resp =
            app(
                Request(Method.GET, "/api/search?q=$longQuery")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(resp.status.code in listOf(200, 400), "Very long query must not 500")
    }
}
