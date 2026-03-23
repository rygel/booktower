package org.runary.integration

import org.runary.TestFixture
import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SidecarMetadataIntegrationTest : IntegrationTestBase() {
    @TempDir
    lateinit var tmp: Path

    private val jdbi = TestFixture.database.getJdbi()

    @Test
    fun `apply-sidecar-metadata reads opf file and updates book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Title")

        // Write an OPF sidecar next to a "fake" book file
        val bookFile = tmp.resolve("Dune.epub").toFile().also { it.writeText("fake") }
        tmp.resolve("Dune.opf").toFile().writeText(
            """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Dune</dc:title>
    <dc:creator>Frank Herbert</dc:creator>
    <dc:publisher>Ace Books</dc:publisher>
    <dc:date>1965</dc:date>
  </metadata>
</package>""",
        )

        // Point the book's file_path at the epub in our temp dir.
        // Raw SQL is acceptable here: file_path is internal state set during library scanning,
        // and there is no BookService method to update just file_path on an existing book.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                .bind(0, bookFile.absolutePath)
                .bind(1, bookId)
                .execute()
        }

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/apply-sidecar-metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("opf", tree.get("source").asText())

        // Verify the book was updated in the DB
        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val book = Json.mapper.readTree(bookResp.bodyString())
        assertEquals("Dune", book.get("title").asText())
        assertEquals("Frank Herbert", book.get("author").asText())
        assertEquals("Ace Books", book.get("publisher").asText())
    }

    @Test
    fun `apply-sidecar-metadata returns 404 when no sidecar exists`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "NoSidecar")

        val bookFile = tmp.resolve("NoSidecar.epub").toFile().also { it.writeText("fake") }
        // Raw SQL is acceptable here: file_path is internal state with no service method to update it.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                .bind(0, bookFile.absolutePath)
                .bind(1, bookId)
                .execute()
        }

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/apply-sidecar-metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `scan picks up sidecar opf automatically`() {
        // Create a temp library directory containing a book + OPF sidecar
        val libDir = tmp.resolve("scanlib").toFile().also { it.mkdirs() }
        File(libDir, "Foundation.epub").writeText("fake epub")
        File(libDir, "Foundation.opf").writeText(
            """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Foundation</dc:title>
    <dc:creator>Isaac Asimov</dc:creator>
  </metadata>
</package>""",
        )

        val token = registerAndGetToken("sidecar_scan")
        // Create library pointing at our temp dir
        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"SidecarScanLib","path":"${libDir.absolutePath.replace("\\", "\\\\")}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()

        // Scan the library
        app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        // Verify the book has author from sidecar
        val booksResp = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token"))
        val books = Json.mapper.readTree(booksResp.bodyString())
        val items = books.get("books") ?: books // handle both wrapped and unwrapped
        val foundBook =
            (if (items.isArray) items else books.get("books"))
                ?.firstOrNull { it.get("title")?.asText() == "Foundation" }
        assertEquals("Foundation", foundBook?.get("title")?.asText())
        assertEquals(
            "Isaac Asimov",
            foundBook?.get("author")?.asText(),
            "Author should be populated from sidecar OPF",
        )
    }

    @Test
    fun `apply-sidecar-metadata requires authentication`() {
        val resp = app(Request(Method.POST, "/api/books/some-id/apply-sidecar-metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
