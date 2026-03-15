package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BulkOperationsIntegrationTest : IntegrationTestBase() {

    @Test
    fun `bulk delete requires auth`() {
        val resp = app(
            Request(Method.POST, "/api/books/bulk/delete")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":[]}"""),
        )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `bulk move requires auth`() {
        val resp = app(
            Request(Method.POST, "/api/books/bulk/move")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":[],"targetLibraryId":"00000000-0000-0000-0000-000000000000"}"""),
        )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `bulk delete returns 400 for empty bookIds`() {
        val token = registerAndGetToken("bulk")
        val resp = app(
            Request(Method.POST, "/api/books/bulk/delete")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":[]}"""),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `bulk delete removes owned books`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Bulk Delete 1")
        val book2 = createBook(token, libId, "Bulk Delete 2")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/delete")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$book1","$book2"]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(2, body.get("deleted").asInt())
    }

    @Test
    fun `bulk delete does not affect another user's books`() {
        val token1 = registerAndGetToken("bulk")
        val token2 = registerAndGetToken("bulk")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId, "Other User Book")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/delete")
                .header("Cookie", "token=$token2")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$bookId"]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        // Count should be 0 — user2 doesn't own the book
        assertEquals(0, body.get("deleted").asInt())
    }

    @Test
    fun `bulk move transfers books between libraries`() {
        val token = registerAndGetToken("bulk")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val book1 = createBook(token, lib1, "Move Me 1")
        val book2 = createBook(token, lib1, "Move Me 2")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/move")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$book1","$book2"],"targetLibraryId":"$lib2"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(2, body.get("moved").asInt())
    }

    @Test
    fun `bulk move returns 0 for non-existent target library`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.POST, "/api/books/bulk/move")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$bookId"],"targetLibraryId":"00000000-0000-0000-0000-000000000000"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, body.get("moved").asInt())
    }

    @Test
    fun `bulk tag sets tags on multiple books`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Tag Me 1")
        val book2 = createBook(token, libId, "Tag Me 2")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/tag")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$book1","$book2"],"tags":["sci-fi","classic"]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(2, body.get("updated").asInt())
    }

    @Test
    fun `bulk tag with empty tags clears all tags`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Clear Tags")

        // Set tags first
        app(
            Request(Method.POST, "/api/books/bulk/tag")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$bookId"],"tags":["fiction"]}"""),
        )

        // Clear them
        val resp = app(
            Request(Method.POST, "/api/books/bulk/tag")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$bookId"],"tags":[]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, body.get("updated").asInt())
    }

    @Test
    fun `bulk status sets read status on multiple books`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Status 1")
        val book2 = createBook(token, libId, "Status 2")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$book1","$book2"],"status":"READING"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(2, body.get("updated").asInt())
    }

    @Test
    fun `bulk status clears status with NONE`() {
        val token = registerAndGetToken("bulk")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Clear Status")

        val resp = app(
            Request(Method.POST, "/api/books/bulk/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["$bookId"],"status":"NONE"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, body.get("updated").asInt())
    }

    @Test
    fun `bulk move returns 400 for invalid targetLibraryId`() {
        val token = registerAndGetToken("bulk")
        val resp = app(
            Request(Method.POST, "/api/books/bulk/move")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookIds":["00000000-0000-0000-0000-000000000001"],"targetLibraryId":"not-a-uuid"}"""),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `ScanScheduleService start does not throw with interval 0`() {
        // Interval 0 = disabled; start() should be a no-op
        val service = org.booktower.services.ScanScheduleService(
            org.booktower.TestFixture.database.getJdbi(),
            org.booktower.services.LibraryService(
                org.booktower.TestFixture.database.getJdbi(),
                org.booktower.services.PdfMetadataService(
                    org.booktower.TestFixture.database.getJdbi(),
                    org.booktower.TestFixture.config.storage.coversPath,
                ),
            ),
            0L,
        )
        service.start() // should not throw
        service.stop()  // safe to call even when disabled
        assertTrue(true)
    }
}
