package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.UserExportDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that the export endpoint produces complete, accurate data
 * that matches what was created through the API.
 */
class ExportRoundTripIntegrationTest : IntegrationTestBase() {

    @Test
    fun `export contains all created libraries and books with correct data`() {
        val token = registerAndGetToken("export_rt")

        // Create two libraries with books
        val lib1 = createLibrary(token, "Fiction Library")
        val lib2 = createLibrary(token, "Non-Fiction Library")

        val book1 = createBook(token, lib1, "The Test Novel")
        val book2 = createBook(token, lib1, "Another Story")
        val book3 = createBook(token, lib2, "Science Stuff")

        // Set status and rating on book1
        app(
            Request(Method.POST, "/api/books/$book1/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"status":"FINISHED"}"""),
        )
        app(
            Request(Method.POST, "/ui/books/$book1/rating")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("rating=5"),
        )

        // Set tags on book2
        app(
            Request(Method.POST, "/ui/books/$book2/tags")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("tags=fiction,adventure"),
        )

        // Create a bookmark on book1
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$book1","page":42,"title":"Key passage","note":"Remember this part"}"""),
        )

        // Export
        val exportResp =
            app(Request(Method.GET, "/api/export").header("Cookie", "token=$token"))
        assertEquals(Status.OK, exportResp.status)
        assertTrue(
            exportResp.header("Content-Type")?.contains("json") == true,
            "Export should be JSON",
        )
        assertTrue(
            exportResp.header("Content-Disposition")?.contains("booktower-export") == true,
            "Export should have download filename",
        )

        val export = Json.mapper.readValue(exportResp.bodyString(), UserExportDto::class.java)

        // Verify user info
        assertTrue(export.username.startsWith("export_rt"), "Username should match")
        assertTrue(export.email.contains("@test.com"), "Email should be present")
        assertNotNull(export.memberSince, "Member since should be set")

        // Verify libraries
        assertEquals(2, export.libraries.size, "Should export 2 libraries")
        val fictionLib = export.libraries.first { it.name == "Fiction Library" }
        val nonFictionLib = export.libraries.first { it.name == "Non-Fiction Library" }

        // Verify books in fiction library
        assertEquals(2, fictionLib.books.size, "Fiction library should have 2 books")
        val novel = fictionLib.books.first { it.title == "The Test Novel" }
        val story = fictionLib.books.first { it.title == "Another Story" }

        // Verify book1 status, rating
        assertEquals("FINISHED", novel.status, "Book1 status should be FINISHED")
        assertEquals(5, novel.rating, "Book1 rating should be 5")

        // Verify book1 bookmark
        assertTrue(novel.bookmarks.isNotEmpty(), "Book1 should have bookmarks")
        val bookmark = novel.bookmarks.first()
        assertEquals(42, bookmark.page, "Bookmark page should be 42")
        assertEquals("Key passage", bookmark.title, "Bookmark title should match")
        assertEquals("Remember this part", bookmark.note, "Bookmark note should match")

        // Verify book2 tags
        assertTrue(story.tags.contains("fiction"), "Book2 should have 'fiction' tag")
        assertTrue(story.tags.contains("adventure"), "Book2 should have 'adventure' tag")

        // Verify non-fiction library
        assertEquals(1, nonFictionLib.books.size, "Non-fiction library should have 1 book")
        assertEquals("Science Stuff", nonFictionLib.books.first().title)
    }

    @Test
    fun `export for user with no data returns empty libraries`() {
        val token = registerAndGetToken("export_empty")
        val exportResp =
            app(Request(Method.GET, "/api/export").header("Cookie", "token=$token"))
        assertEquals(Status.OK, exportResp.status)
        val export = Json.mapper.readValue(exportResp.bodyString(), UserExportDto::class.java)
        assertTrue(export.libraries.isEmpty(), "New user should have no libraries in export")
    }

    @Test
    fun `export is isolated between users`() {
        val token1 = registerAndGetToken("export_iso1")
        val token2 = registerAndGetToken("export_iso2")

        createLibrary(token1, "User1 Secret Library")
        createLibrary(token2, "User2 Public Library")

        val export1 = Json.mapper.readValue(
            app(Request(Method.GET, "/api/export").header("Cookie", "token=$token1")).bodyString(),
            UserExportDto::class.java,
        )
        val export2 = Json.mapper.readValue(
            app(Request(Method.GET, "/api/export").header("Cookie", "token=$token2")).bodyString(),
            UserExportDto::class.java,
        )

        assertEquals(1, export1.libraries.size)
        assertEquals("User1 Secret Library", export1.libraries.first().name)
        assertTrue(export1.libraries.none { it.name == "User2 Public Library" })

        assertEquals(1, export2.libraries.size)
        assertEquals("User2 Public Library", export2.libraries.first().name)
        assertTrue(export2.libraries.none { it.name == "User1 Secret Library" })
    }
}
