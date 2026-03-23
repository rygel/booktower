package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExternalIdsIntegrationTest : IntegrationTestBase() {
    @Test
    fun `PUT external-ids sets IDs on a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/external-ids")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"goodreadsId":"gr123","openlibraryId":"OL12345W","googleBooksId":"gbk456"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("gr123", tree.get("goodreadsId")?.asText())
        assertEquals("OL12345W", tree.get("openlibraryId")?.asText())
        assertEquals("gbk456", tree.get("googleBooksId")?.asText())
    }

    @Test
    fun `GET book returns external ID fields`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/external-ids")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"hardcoverId":"hc789","comicvineId":"cv321","audibleId":"adbl999"}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("hc789", tree.get("hardcoverId")?.asText())
        assertEquals("cv321", tree.get("comicvineId")?.asText())
        assertEquals("adbl999", tree.get("audibleId")?.asText())
    }

    @Test
    fun `PUT external-ids on nonexistent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/external-ids")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"goodreadsId":"x"}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `external-ids endpoint requires authentication`() {
        val resp = app(Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/external-ids"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
