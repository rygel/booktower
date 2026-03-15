package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.ScanResult
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanIntegrationTest : IntegrationTestBase() {

    private val tempDirs = mutableListOf<File>()

    @AfterEach
    fun cleanupTempDirs() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    private fun createTempLibraryDir(): File {
        val dir = Files.createTempDirectory("booktower-scan-test").toFile()
        tempDirs += dir
        return dir
    }

    private fun createLibraryWithPath(token: String, path: String): String {
        val name = "ScanLib ${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"$name","path":"${path.replace("\\", "\\\\")}"}"""),
        )
        assertEquals(Status.CREATED, response.status)
        return Json.mapper.readTree(response.bodyString()).get("id").asText()
    }

    @Test
    fun `scan empty directory returns zero counts`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(0, result.added)
        assertEquals(0, result.skipped)
        assertEquals(0, result.errors)
        assertTrue(result.books.isEmpty())
    }

    @Test
    fun `scan finds supported files and imports them`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        File(dir, "book_one.pdf").writeText("fake pdf content")
        File(dir, "novel.epub").writeText("fake epub content")
        File(dir, "readme.txt").writeText("should be ignored")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(2, result.added)
        assertEquals(0, result.skipped)
        assertEquals(0, result.errors)
        assertEquals(2, result.books.size)
    }

    @Test
    fun `scan skips already-imported files on second scan`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        File(dir, "my_book.pdf").writeText("fake pdf content")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        // First scan
        val first = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, first.status)
        val firstResult = Json.mapper.readValue(first.bodyString(), ScanResult::class.java)
        assertEquals(1, firstResult.added)

        // Second scan — same file must be skipped
        val second = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, second.status)
        val secondResult = Json.mapper.readValue(second.bodyString(), ScanResult::class.java)
        assertEquals(0, secondResult.added)
        assertEquals(1, secondResult.skipped)
    }

    @Test
    fun `scan creates book with cleaned-up title from filename`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        File(dir, "the_great_gatsby.pdf").writeText("fake content")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(1, result.added)
        // underscores should be replaced with spaces
        assertTrue(result.books[0].title.contains("the great gatsby"), "Expected cleaned title, got: ${result.books[0].title}")
    }

    @Test
    fun `scan walks subdirectories`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        val sub = File(dir, "subfolder").also { it.mkdir() }
        File(dir, "root.pdf").writeText("fake content")
        File(sub, "nested.epub").writeText("fake content")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(2, result.added)
    }

    @Test
    fun `scan all supported extensions are imported`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        listOf("a.pdf", "b.epub", "c.mobi", "d.cbz", "e.cbr", "f.fb2").forEach {
            File(dir, it).writeText("content")
        }
        File(dir, "ignore.docx").writeText("not supported")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )

        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(6, result.added)
    }

    @Test
    fun `scan without authentication returns 401`() {
        val token = registerAndGetToken("scan")
        val dir = createTempLibraryDir()
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val response = app(
            Request(Method.POST, "/api/libraries/$libId/scan"),
        )

        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `scan nonexistent library returns empty result`() {
        val token = registerAndGetToken("scan")
        val fakeId = "00000000-0000-0000-0000-000000000000"

        val response = app(
            Request(Method.POST, "/api/libraries/$fakeId/scan")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(0, result.added)
    }

    @Test
    fun `user B cannot scan user A library`() {
        val tokenA = registerAndGetToken("scan_a")
        val tokenB = registerAndGetToken("scan_b")
        val dir = createTempLibraryDir()
        File(dir, "book.pdf").writeText("content")
        val libIdA = createLibraryWithPath(tokenA, dir.absolutePath)

        // User B tries to scan user A's library
        val response = app(
            Request(Method.POST, "/api/libraries/$libIdA/scan")
                .header("Cookie", "token=$tokenB"),
        )

        // Library not found for user B => empty result (not an error, just 0 added)
        assertEquals(Status.OK, response.status)
        val result = Json.mapper.readValue(response.bodyString(), ScanResult::class.java)
        assertEquals(0, result.added)
    }
}
