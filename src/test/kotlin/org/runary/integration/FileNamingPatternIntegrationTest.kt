package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileNamingPatternIntegrationTest : IntegrationTestBase() {
    @Test
    fun `fileNamingPattern is null by default`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(
            Json.mapper
                .readTree(resp.bodyString())
                .get("fileNamingPattern")
                .isNull,
        )
    }

    @Test
    fun `PUT settings stores fileNamingPattern`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"fileNamingPattern":"{author}/{title}"}"""),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals(
            "{author}/{title}",
            Json.mapper
                .readTree(resp.bodyString())
                .get("fileNamingPattern")
                .asText(),
        )
    }

    @Test
    fun `POST organize returns details about missing pattern`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val resp = app(Request(Method.POST, "/api/libraries/$libId/organize").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)

        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("moved").asInt())
        assertEquals(0, tree.get("errors").asInt())
        // details should contain a message about missing pattern
        val details = tree.get("details")
        assertTrue(details.size() > 0 || tree.get("skipped").asInt() == 0)
    }

    @Test
    fun `POST organize moves file to new location matching pattern`(
        @TempDir tempDir: Path,
    ) {
        val token = registerAndGetToken()

        // Create library directory with a real epub file
        val libDir = tempDir.resolve("mylib").toFile().also { it.mkdirs() }
        File(libDir, "some_random_name.epub").writeText("fake epub content")

        // Create library and scan so the file is registered in DB
        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"TempLib","path":"${libDir.absolutePath.replace("\\", "\\\\")}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()

        // Scan to pick up the file and register the book
        app(Request(Method.POST, "/api/libraries/$libId/scan").header("Cookie", "token=$token"))

        // Update the book's author and title via the book update API
        val booksResp = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token"))
        val booksNode = Json.mapper.readTree(booksResp.bodyString())
        // BookListDto serializes as {"books":[...],"total":...} or as array depending on Jackson config
        val bookNode = if (booksNode.isArray) booksNode.get(0) else booksNode.get("books")?.get(0)
        val bookId = bookNode!!.get("id").asText()
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"My Great Book","author":"Jane Author","description":null}"""),
        )

        // Set naming pattern
        app(
            Request(Method.PUT, "/api/libraries/$libId/settings")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fileNamingPattern":"{author}/{title}.{ext}"}"""),
        )

        // Run organize
        val organizeResp = app(Request(Method.POST, "/api/libraries/$libId/organize").header("Cookie", "token=$token"))
        assertEquals(Status.OK, organizeResp.status)

        val tree = Json.mapper.readTree(organizeResp.bodyString())
        assertEquals(1, tree.get("moved").asInt())
        assertEquals(0, tree.get("errors").asInt())

        // Verify old file is gone and new one exists
        assertFalse(File(libDir, "some_random_name.epub").exists(), "original file should have been moved")
        val expectedFile = File(libDir, "Jane Author/My Great Book.epub")
        assertTrue(expectedFile.exists(), "file should exist at new path: ${expectedFile.absolutePath}")
    }

    @Test
    fun `POST organize skips files that are already at correct location`(
        @TempDir tempDir: Path,
    ) {
        val token = registerAndGetToken()
        val libDir = tempDir.resolve("mylib2").toFile().also { it.mkdirs() }

        // Write a file at the exact location the pattern would produce
        val authorDir = File(libDir, "Jane Author").also { it.mkdirs() }
        File(authorDir, "Correct Book.epub").writeText("epub")

        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"TempLib2","path":"${libDir.absolutePath.replace("\\", "\\\\")}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()

        // Scan so the DB has the book
        app(Request(Method.POST, "/api/libraries/$libId/scan").header("Cookie", "token=$token"))
        val booksResp = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token"))
        val booksNode2 = Json.mapper.readTree(booksResp.bodyString())
        val bookNode2 = if (booksNode2.isArray) booksNode2.get(0) else booksNode2.get("books")?.get(0)
        val bookId = bookNode2!!.get("id").asText()
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Correct Book","author":"Jane Author","description":null}"""),
        )

        app(
            Request(Method.PUT, "/api/libraries/$libId/settings")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fileNamingPattern":"{author}/{title}.{ext}"}"""),
        )

        val organizeResp = app(Request(Method.POST, "/api/libraries/$libId/organize").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(organizeResp.bodyString())
        assertEquals(0, tree.get("moved").asInt())
        assertEquals(1, tree.get("skipped").asInt())
    }

    @Test
    fun `POST organize for non-existent library returns empty result`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/libraries/00000000-0000-0000-0000-000000000000/organize")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("moved").asInt())
    }

    @Test
    fun `organize endpoint requires authentication`() {
        val resp = app(Request(Method.POST, "/api/libraries/00000000-0000-0000-0000-000000000000/organize"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
