package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

/**
 * End-to-end test for custom metadata fields on the book detail page.
 * Verifies the book page HTML contains the custom fields section
 * and that the API works for CRUD operations.
 */
class CustomFieldsPageTest : IntegrationTestBase() {
    @Test
    fun `book detail page has custom fields section`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cf-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Custom Fields Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("custom-fields-section"), "Book page should have custom fields section")
        assertTrue(html.contains("cf-name"), "Should have field name input")
        assertTrue(html.contains("cf-value"), "Should have field value input")
        assertTrue(html.contains("cf-add-btn"), "Should have add button")
    }

    @Test
    fun `custom field API set and get works`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cf-api-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"API Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        // Set a custom field
        val setResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/custom-fields")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"fieldName":"Recommended by","fieldValue":"Alice"}"""),
            )
        assertEquals(Status.NO_CONTENT, setResp.status)

        // Get custom fields
        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId/custom-fields")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, getResp.status)
        val fields = Json.mapper.readTree(getResp.bodyString())
        assertTrue(fields.any { it.get("fieldName").asText() == "Recommended by" && it.get("fieldValue").asText() == "Alice" })
    }

    @Test
    fun `custom field can be deleted`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "cf-del-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Delete Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        // Set then delete
        app(
            Request(Method.PUT, "/api/books/$bookId/custom-fields")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fieldName":"Temp Field","fieldValue":"temp"}"""),
        )
        val delResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/custom-fields/Temp%20Field")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)

        // Verify gone
        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId/custom-fields")
                    .header("Cookie", "token=$token"),
            )
        val fields = Json.mapper.readTree(getResp.bodyString())
        assertTrue(fields.none { it.get("fieldName").asText() == "Temp Field" })
    }
}
