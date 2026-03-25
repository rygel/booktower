package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.runary.config.Json
import org.runary.services.ComicService
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComicIntegrationTest : IntegrationTestBase() {
    @Test
    fun `comic pages endpoint returns pageCount 0 for book without file`() {
        val token = registerAndGetToken("comic")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/comic/pages")
                    .header("Cookie", "token=$token"),
            )
        // file_path stored as empty string → pageCount is 0, endpoint still returns 200
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, body.get("pageCount")?.asInt())
    }

    @Test
    fun `comic page image endpoint returns 404 for book without file`() {
        val token = registerAndGetToken("comic")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/comic/0")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `comic pages endpoint requires auth`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/comic/pages"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `comic page endpoint requires auth`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/comic/0"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `ComicService reads CBZ page count correctly`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = tmpDir.resolve("test.cbz")
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            listOf("001.jpg", "002.png", "003.jpg").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // minimal JPEG header
                zos.closeEntry()
            }
        }

        val service = ComicService()
        val count = service.getPageCount(cbz.toString())
        assertEquals(3, count)
    }

    @Test
    fun `ComicService lists CBZ pages sorted by name`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = tmpDir.resolve("sorted.cbz")
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            listOf("003.jpg", "001.png", "002.jpg").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(byteArrayOf(0x89.toByte(), 0x50.toByte())) // PNG-ish
                zos.closeEntry()
            }
        }

        val service = ComicService()
        val pages = service.listPages(cbz.toString())
        assertEquals(3, pages.size)
        assertEquals("001.png", pages[0].name)
        assertEquals("002.jpg", pages[1].name)
        assertEquals("003.jpg", pages[2].name)
    }

    @Test
    fun `ComicService reads specific CBZ page bytes`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = tmpDir.resolve("pages.cbz")
        val pageData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xAB.toByte(), 0xCD.toByte())
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("001.jpg"))
            zos.write(pageData)
            zos.closeEntry()
        }

        val service = ComicService()
        val bytes = service.getPage(cbz.toString(), 0)
        assertNotNull(bytes)
        assertTrue(bytes.contentEquals(pageData))
    }

    @Test
    fun `ComicService returns null for out-of-range page`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = tmpDir.resolve("one.cbz")
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("001.jpg"))
            zos.write(byteArrayOf(0x00))
            zos.closeEntry()
        }

        val service = ComicService()
        val bytes = service.getPage(cbz.toString(), 99)
        assertEquals(null, bytes)
    }

    @Test
    fun `ComicService returns empty list for non-existent file`() {
        val service = ComicService()
        val pages = service.listPages("/nonexistent/path/file.cbz")
        assertTrue(pages.isEmpty())
    }
}
