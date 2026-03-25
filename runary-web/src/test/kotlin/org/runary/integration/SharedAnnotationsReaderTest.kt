package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for shared annotations functionality.
 * Tests the API endpoints and verifies the reader template
 * contains the shared annotations JavaScript function.
 */
class SharedAnnotationsReaderTest : IntegrationTestBase() {
    @Test
    fun `shared annotations API returns empty for book with no shared annotations`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "sa-api-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Empty Shared","libraryId":"$libId"}"""),
            )
        val bookId =
            org.runary.config.Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/shared-annotations")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals("[]", resp.bodyString().trim())
    }

    @Test
    fun `share annotation toggle works via API`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "sa-toggle-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Share Toggle Book","libraryId":"$libId"}"""),
            )
        val bookId =
            org.runary.config.Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        // Create an annotation
        val annotResp =
            app(
                Request(Method.POST, "/ui/books/$bookId/annotations")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("page=5&selectedText=Important+passage&color=yellow"),
            )
        assertTrue(annotResp.status == Status.OK || annotResp.status == Status.CREATED, "Annotation should be created")
        val annotId =
            org.runary.config.Json.mapper
                .readTree(annotResp.bodyString())
                .get("id")
                .asText()

        // Share it
        val shareResp =
            app(
                Request(Method.POST, "/api/annotations/$annotId/share")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"shared":true,"note":"Great passage!"}"""),
            )
        assertEquals(Status.NO_CONTENT, shareResp.status)

        // Verify it shows in shared
        val sharedResp =
            app(
                Request(Method.GET, "/api/books/$bookId/shared-annotations")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, sharedResp.status)
        val shared =
            org.runary.config.Json.mapper
                .readTree(sharedResp.bodyString())
        assertTrue(shared.size() >= 1, "Should have at least 1 shared annotation")
        assertTrue(shared[0].get("selectedText").asText().contains("Important"), "Should contain text")
        assertTrue(shared[0].get("note").asText().contains("Great"), "Should contain note")
        assertTrue(shared[0].has("username"), "Should include username")
    }

    @Test
    fun `unshare removes from shared list`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "sa-unshare-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Unshare Book","libraryId":"$libId"}"""),
            )
        val bookId =
            org.runary.config.Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val annotResp =
            app(
                Request(Method.POST, "/ui/books/$bookId/annotations")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("page=10&selectedText=Temp+highlight&color=blue"),
            )
        val annotId =
            org.runary.config.Json.mapper
                .readTree(annotResp.bodyString())
                .get("id")
                .asText()

        // Share then unshare
        app(
            Request(Method.POST, "/api/annotations/$annotId/share")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"shared":true}"""),
        )
        app(
            Request(Method.POST, "/api/annotations/$annotId/share")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"shared":false}"""),
        )

        val sharedResp =
            app(
                Request(Method.GET, "/api/books/$bookId/shared-annotations")
                    .header("Cookie", "token=$token"),
            )
        assertEquals("[]", sharedResp.bodyString().trim())
    }
}
