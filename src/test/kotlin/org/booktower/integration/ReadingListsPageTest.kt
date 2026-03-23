package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the Reading Lists page.
 */
class ReadingListsPageTest : IntegrationTestBase() {
    @Test
    fun `reading lists page requires authentication`() {
        val resp = app(Request(Method.GET, "/reading-lists"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `reading lists page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/reading-lists").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-list-btn"), "Page should have create button")
        assertTrue(html.contains("create-list-form"), "Page should have create form")
    }

    @Test
    fun `reading lists page shows created list`() {
        val token = registerAndGetToken()
        app(
            Request(Method.POST, "/api/reading-lists")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Summer Reading 2026","description":"Books for the beach"}"""),
        )

        val resp = app(Request(Method.GET, "/reading-lists").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Summer Reading 2026"), "Page should show list name")
        assertTrue(html.contains("Books for the beach"), "Page should show list description")
        assertTrue(html.contains("0 / 0"), "Should show 0/0 completed")
    }

    @Test
    fun `reading lists page has delete button`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/reading-lists")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Deletable List"}"""),
            )
        val listId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/reading-lists").header("Cookie", "token=$token"))
        val html = resp.bodyString()
        assertTrue(html.contains("rl-delete"), "Should have delete button")
        assertTrue(html.contains(listId), "Should have list ID in data attribute")
    }

    @Test
    fun `sidebar contains reading lists link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/reading-lists\""), "Sidebar should have /reading-lists link")
    }

    @Test
    fun `reading list shows progress bar when items added`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "rl-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"List Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val listResp =
            app(
                Request(Method.POST, "/api/reading-lists")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Progress List"}"""),
            )
        val listId =
            Json.mapper
                .readTree(listResp.bodyString())
                .get("id")
                .asText()

        app(
            Request(Method.POST, "/api/reading-lists/$listId/books/$bookId")
                .header("Cookie", "token=$token"),
        )

        val resp = app(Request(Method.GET, "/reading-lists").header("Cookie", "token=$token"))
        val html = resp.bodyString()
        assertTrue(html.contains("0 / 1"), "Should show 0/1 completed")
    }
}
