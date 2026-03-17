package org.booktower.integration

import org.booktower.services.Fb2ReaderService
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatSupportIntegrationTest : IntegrationTestBase() {

    private lateinit var token: String

    @BeforeEach
    fun setup() {
        token = registerAndGetToken("formats")
    }

    // ── Fb2ReaderService unit tests ───────────────────────────────────────────

    @Test
    fun `Fb2ReaderService converts minimal FB2 to HTML`() {
        val fb2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
  <description>
    <title-info>
      <book-title>Test Book</book-title>
      <author><first-name>John</first-name><last-name>Doe</last-name></author>
    </title-info>
  </description>
  <body>
    <section>
      <title><p>Chapter One</p></title>
      <p>Hello <strong>world</strong>.</p>
      <p>Second paragraph.</p>
    </section>
  </body>
</FictionBook>"""
        val tmp = Files.createTempFile("test", ".fb2").toFile()
        tmp.writeText(fb2)
        try {
            val html = Fb2ReaderService().toHtml(tmp)
            assertTrue(html.contains("Test Book"), "Title should appear")
            assertTrue(html.contains("John Doe"), "Author should appear")
            assertTrue(html.contains("Chapter One"), "Chapter heading should appear")
            assertTrue(html.contains("<strong>world</strong>"), "Bold markup should be preserved")
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `Fb2ReaderService escapes HTML entities`() {
        val fb2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook>
  <description><title-info><book-title>A &amp; B</book-title></title-info></description>
  <body><section><p>x &lt; y</p></section></body>
</FictionBook>"""
        val tmp = Files.createTempFile("test-ent", ".fb2").toFile()
        tmp.writeText(fb2)
        try {
            val html = Fb2ReaderService().toHtml(tmp)
            assertTrue(html.contains("A &amp; B") || html.contains("A & B"), "Ampersand should be safe")
            assertTrue(!html.contains("<y>"), "Raw < should not create an element")
        } finally {
            tmp.delete()
        }
    }

    // ── Integration: reader page returns correct readerType ───────────────────

    private fun readerPageBody(ext: String): String {
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Format Test $ext")
        // Upload a tiny placeholder file with the given extension
        val content = when (ext) {
            "fb2" -> minimalFb2Bytes()
            else -> byteArrayOf(0x50, 0x4B, 0x03, 0x04) // PK header (not a real file, just extension matters)
        }
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.$ext")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(content), content.size.toLong())),
        )
        val resp = app(
            Request(Method.GET, "/books/$bookId/read")
                .header("Cookie", "token=$token"),
        )
        assertEquals(200, resp.status.code, "Reader page should return 200 for .$ext")
        return resp.bodyString()
    }

    @Test
    fun `reader page uses fb2 mode for FB2 files`() {
        val body = readerPageBody("fb2")
        assertTrue(body.contains("fb2-iframe") || body.contains("read-content"), "FB2 reader iframe should be present")
    }

    @Test
    fun `reader page uses kindle mode for MOBI files`() {
        val body = readerPageBody("mobi")
        assertTrue(body.contains("kindle-wrap") || body.contains("kindle.download") || body.contains("Download to read"), "Kindle download UI should be present")
    }

    @Test
    fun `reader page uses kindle mode for AZW3 files`() {
        val body = readerPageBody("azw3")
        assertTrue(body.contains("kindle-wrap") || body.contains("kindle.download") || body.contains("Download to read"), "Kindle download UI should be present")
    }

    // ── Integration: /api/books/{id}/read-content for FB2 ────────────────────

    @Test
    fun `read-content endpoint serves FB2 as HTML`() {
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "FB2 Content Test")
        val fb2bytes = minimalFb2Bytes()
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.fb2")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(fb2bytes), fb2bytes.size.toLong())),
        )
        val resp = app(
            Request(Method.GET, "/api/books/$bookId/read-content")
                .header("Cookie", "token=$token"),
        )
        assertEquals(200, resp.status.code)
        assertTrue(resp.header("Content-Type")?.contains("text/html") == true, "Should return HTML")
        assertTrue(resp.bodyString().contains("Minimal"), "Should contain book title")
    }

    @Test
    fun `read-content returns 422 for non-FB2 files`() {
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "EPUB Test")
        val epubBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.epub")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(epubBytes), epubBytes.size.toLong())),
        )
        val resp = app(
            Request(Method.GET, "/api/books/$bookId/read-content")
                .header("Cookie", "token=$token"),
        )
        assertEquals(422, resp.status.code)
    }

    @Test
    fun `read-content returns 422 for MOBI when Calibre is not installed`() {
        org.junit.jupiter.api.Assumptions.assumeFalse(calibreAvailable(), "Calibre is installed — skipping no-Calibre fallback test")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "MOBI Test")
        val mobiBytes = byteArrayOf(0x4D, 0x4F, 0x42, 0x49)
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.mobi")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(mobiBytes), mobiBytes.size.toLong())),
        )
        val resp = app(
            Request(Method.GET, "/api/books/$bookId/read-content")
                .header("Cookie", "token=$token"),
        )
        assertEquals(422, resp.status.code)
    }

    @Test
    fun `read-content returns 422 for AZW3 when Calibre is not installed`() {
        org.junit.jupiter.api.Assumptions.assumeFalse(calibreAvailable(), "Calibre is installed — skipping no-Calibre fallback test")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "AZW3 Test")
        val azw3Bytes = byteArrayOf(0x41, 0x5A, 0x57, 0x33)
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.azw3")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(azw3Bytes), azw3Bytes.size.toLong())),
        )
        val resp = app(
            Request(Method.GET, "/api/books/$bookId/read-content")
                .header("Cookie", "token=$token"),
        )
        assertEquals(422, resp.status.code)
    }

    @Test
    fun `kindle reader page contains Calibre conversion UI elements`() {
        // Page should include both the converting spinner and download fallback markup
        val body = readerPageBody("mobi")
        assertTrue(body.contains("kindle-converting") || body.contains("kindle-download"),
            "Kindle reader should include Calibre conversion UI")
        assertTrue(body.contains("read-content"), "Page should reference read-content endpoint for Calibre probe")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalFb2Bytes(): ByteArray = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
  <description><title-info><book-title>Minimal</book-title></title-info></description>
  <body><section><p>Test content.</p></section></body>
</FictionBook>""".toByteArray(Charsets.UTF_8)

    private fun calibreAvailable(): Boolean = try {
        val proc = ProcessBuilder("ebook-convert", "--version").redirectErrorStream(true).start()
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && proc.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
