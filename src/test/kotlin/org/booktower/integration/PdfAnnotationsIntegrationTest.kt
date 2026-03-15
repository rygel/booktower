package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfAnnotationsIntegrationTest : IntegrationTestBase() {

    private fun postAnnotation(token: String, bookId: String, page: Int = 1, text: String = "selected text", color: String = "yellow"): org.http4k.core.Response =
        app(Request(Method.POST, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("page=$page&selectedText=${java.net.URLEncoder.encode(text, "UTF-8")}&color=$color"))

    // ── GET annotations ──────────────────────────────────────────────────────

    @Test
    fun `GET annotations returns 200 with empty array for new book`() {
        val token = registerAndGetToken("an1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("[]"), "Should return empty JSON array")
    }

    @Test
    fun `GET annotations requires authentication`() {
        val token = registerAndGetToken("an2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/ui/books/$bookId/annotations"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ── POST create annotation ────────────────────────────────────────────────

    @Test
    fun `POST annotation returns 201 with annotation data`() {
        val token = registerAndGetToken("an3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = postAnnotation(token, bookId, 5, "highlighted passage", "yellow")
        assertEquals(Status.CREATED, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("highlighted passage"), "Response should contain selected text")
        assertTrue(body.contains("yellow"), "Response should contain color")
        assertTrue(body.contains("\"page\":5"), "Response should contain page")
    }

    @Test
    fun `POST annotation without selectedText returns 400`() {
        val token = registerAndGetToken("an4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.POST, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("page=1&color=yellow"))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `POST annotation without page returns 400`() {
        val token = registerAndGetToken("an5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.POST, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("selectedText=hello&color=yellow"))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    // ── Created annotation appears in GET ─────────────────────────────────────

    @Test
    fun `created annotation appears in GET list`() {
        val token = registerAndGetToken("an6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        postAnnotation(token, bookId, 3, "my annotation text", "blue")

        val body = app(Request(Method.GET, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("my annotation text"), "Created annotation should appear in list")
        assertTrue(body.contains("blue"), "Color should be preserved")
    }

    @Test
    fun `page filter returns only annotations for that page`() {
        val token = registerAndGetToken("an7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        postAnnotation(token, bookId, 1, "page one text", "yellow")
        postAnnotation(token, bookId, 5, "page five text", "green")

        val body = app(Request(Method.GET, "/ui/books/$bookId/annotations?page=1")
            .header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("page one text"), "Should return page 1 annotation")
        assertFalse(body.contains("page five text"), "Should not return page 5 annotation")
    }

    // ── DELETE annotation ─────────────────────────────────────────────────────

    @Test
    fun `DELETE annotation returns 200`() {
        val token = registerAndGetToken("an8")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val annotJson = postAnnotation(token, bookId).bodyString()
        val annotId = com.fasterxml.jackson.databind.ObjectMapper().readTree(annotJson).get("id").asText()

        val response = app(Request(Method.DELETE, "/ui/annotations/$annotId")
            .header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `deleted annotation not returned in GET`() {
        val token = registerAndGetToken("an9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val annotJson = postAnnotation(token, bookId, 1, "text to delete", "pink").bodyString()
        val annotId = com.fasterxml.jackson.databind.ObjectMapper().readTree(annotJson).get("id").asText()

        app(Request(Method.DELETE, "/ui/annotations/$annotId").header("Cookie", "token=$token"))

        val body = app(Request(Method.GET, "/ui/books/$bookId/annotations")
            .header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("text to delete"), "Deleted annotation should not appear in list")
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    fun `annotations are isolated per user`() {
        val token1 = registerAndGetToken("an10a")
        val token2 = registerAndGetToken("an10b")
        val libId1 = createLibrary(token1)
        val bookId1 = createBook(token1, libId1, "User1 Book")
        postAnnotation(token1, bookId1, 2, "user1 secret note", "yellow")

        val libId2 = createLibrary(token2)
        val bookId2 = createBook(token2, libId2, "User2 Book")

        // User2 cannot see user1's annotations (different book)
        val body2 = app(Request(Method.GET, "/ui/books/$bookId2/annotations")
            .header("Cookie", "token=$token2")).bodyString()
        assertFalse(body2.contains("user1 secret note"), "User2 should not see user1's annotations")

        // User1's annotations still intact
        val body1 = app(Request(Method.GET, "/ui/books/$bookId1/annotations")
            .header("Cookie", "token=$token1")).bodyString()
        assertTrue(body1.contains("user1 secret note"), "User1 should still see own annotations")
    }

    @Test
    fun `user cannot delete another user annotation`() {
        val token1 = registerAndGetToken("an11a")
        val token2 = registerAndGetToken("an11b")
        val libId1 = createLibrary(token1)
        val bookId1 = createBook(token1, libId1)
        val annotJson = postAnnotation(token1, bookId1, 1, "owner annotation", "green").bodyString()
        val annotId = com.fasterxml.jackson.databind.ObjectMapper().readTree(annotJson).get("id").asText()

        // Token2 tries to delete token1's annotation — should return 404 (not found for this user)
        val response = app(Request(Method.DELETE, "/ui/annotations/$annotId")
            .header("Cookie", "token=$token2"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
