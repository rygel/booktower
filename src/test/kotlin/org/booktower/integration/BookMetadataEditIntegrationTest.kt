package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookMetadataEditIntegrationTest : IntegrationTestBase() {
    private fun editBook(
        token: String,
        bookId: String,
        fields: Map<String, String>,
    ): Int {
        val body =
            fields.entries.joinToString("&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/meta")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body),
            )
        return resp.status.code
    }

    private fun bookJson(
        token: String,
        bookId: String,
    ): String = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString()

    // ── ISBN ────────────────────────────────────────────────────────────────────

    @Test
    fun `edit saves ISBN`() {
        val token = registerAndGetToken("bme1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "ISBN Test")

        val status = editBook(token, bookId, mapOf("title" to "ISBN Test", "isbn" to "978-3-16-148410-0"))
        assertEquals(200, status, "Edit should return 200")

        val body = bookJson(token, bookId)
        assertTrue(body.contains("978-3-16-148410-0"), "ISBN should be saved")
    }

    @Test
    fun `edit saves publisher`() {
        val token = registerAndGetToken("bme2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Publisher Test")

        editBook(token, bookId, mapOf("title" to "Publisher Test", "publisher" to "O'Reilly Media"))

        val body = bookJson(token, bookId)
        assertTrue(
            body.contains("O'Reilly Media") || body.contains("O\\u2019Reilly") || body.contains("O%27Reilly"),
            "Publisher should be saved",
        )
    }

    @Test
    fun `edit saves published date`() {
        val token = registerAndGetToken("bme3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Date Test")

        editBook(token, bookId, mapOf("title" to "Date Test", "publishedDate" to "2023-06-15"))

        val body = bookJson(token, bookId)
        assertTrue(body.contains("2023"), "Published date should be saved")
    }

    @Test
    fun `edit saves page count`() {
        val token = registerAndGetToken("bme4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Pages Test")

        editBook(token, bookId, mapOf("title" to "Pages Test", "pageCount" to "320"))

        val body = bookJson(token, bookId)
        assertTrue(body.contains("320"), "Page count should be saved")
    }

    @Test
    fun `edit saves series and series index`() {
        val token = registerAndGetToken("bme5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Series Test")

        editBook(token, bookId, mapOf("title" to "Series Test", "series" to "The Expanse", "seriesIndex" to "1.0"))

        val body = bookJson(token, bookId)
        assertTrue(body.contains("The Expanse"), "Series should be saved")
    }

    @Test
    fun `edit saves all fields together`() {
        val token = registerAndGetToken("bme6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Full Edit Test")

        editBook(
            token,
            bookId,
            mapOf(
                "title" to "Full Edit Updated",
                "author" to "Jane Doe",
                "description" to "A great book",
                "series" to "My Series",
                "seriesIndex" to "2",
                "isbn" to "9780134685991",
                "publisher" to "Addison-Wesley",
                "publishedDate" to "2018-07-11",
                "pageCount" to "464",
            ),
        )

        val body = bookJson(token, bookId)
        assertTrue(body.contains("Full Edit Updated"), "Title should be updated")
        assertTrue(body.contains("Jane Doe"), "Author should be updated")
        assertTrue(body.contains("9780134685991"), "ISBN should be saved")
        assertTrue(body.contains("Addison-Wesley"), "Publisher should be saved")
        assertTrue(body.contains("464"), "Page count should be saved")
    }

    @Test
    fun `edit form fields appear on book detail page`() {
        val token = registerAndGetToken("bme7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Form Test")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("isbn"), "Edit form should include ISBN field")
        assertTrue(body.contains("publisher"), "Edit form should include publisher field")
        assertTrue(body.contains("pageCount"), "Edit form should include pageCount field")
        assertTrue(body.contains("publishedDate"), "Edit form should include publishedDate field")
    }

    @Test
    fun `clearing isbn sets it to null`() {
        val token = registerAndGetToken("bme8")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Clear ISBN Test")

        // First set it
        editBook(token, bookId, mapOf("title" to "Clear ISBN Test", "isbn" to "1234567890"))
        // Then clear it by omitting isbn
        editBook(token, bookId, mapOf("title" to "Clear ISBN Test"))

        val body = bookJson(token, bookId)
        assertTrue(
            !body.contains("1234567890") || body.contains("\"isbn\":null"),
            "ISBN should be cleared",
        )
    }
}
