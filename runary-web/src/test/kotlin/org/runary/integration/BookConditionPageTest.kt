package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

/**
 * End-to-end test for the book condition tracker on the book detail page.
 */
class BookConditionPageTest : IntegrationTestBase() {
    @Test
    fun `book detail page has condition section`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cond-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Condition Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("condition-section"), "Book page should have condition section")
        assertTrue(html.contains("cond-condition"), "Should have condition select")
        assertTrue(html.contains("cond-price"), "Should have price input")
        assertTrue(html.contains("cond-location"), "Should have location input")
    }

    @Test
    fun `condition API set and get works`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cond-api-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"API Condition Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val setResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/condition")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"condition":"good","purchasePrice":"14.99","shelfLocation":"Living room, shelf 2"}"""),
            )
        assertEquals(Status.NO_CONTENT, setResp.status)

        val getResp = app(Request(Method.GET, "/api/books/$bookId/condition").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        val body = getResp.bodyString()
        assertTrue(body.contains("good"), "Should return condition")
        assertTrue(body.contains("14.99"), "Should return price")
        assertTrue(body.contains("Living room"), "Should return location")
    }

    @Test
    fun `condition section has condition options`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cond-opts-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Options Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        val html = resp.bodyString()
        assertTrue(html.contains("mint"), "Should have mint option")
        assertTrue(html.contains("good"), "Should have good option")
        assertTrue(html.contains("fair"), "Should have fair option")
        assertTrue(html.contains("poor"), "Should have poor option")
    }
}
