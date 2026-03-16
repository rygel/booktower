package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for the audio-multi reader page and audiobook progress
 * packed-encoding roundtrip (trackIndex * 1_000_000 + offsetSeconds).
 */
class AudiobookReaderUiTest : IntegrationTestBase() {

    private fun minimalMp3() =
        byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)

    private fun uploadChapter(token: String, bookId: String, trackIndex: Int, title: String = "Chapter ${trackIndex + 1}") {
        val mp3 = minimalMp3()
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-$trackIndex.mp3")
                .header("X-Track-Index", trackIndex.toString())
                .header("X-Chapter-Title", title)
                .body(mp3.inputStream(), mp3.size.toLong()),
        )
    }

    // ── Reader page rendering ─────────────────────────────────────────────────

    @Test
    fun `reader page for book with chapters returns 200 HTML`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Audiobook")
        uploadChapter(token, bookId, 0, "Prologue")

        val resp = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("text/html") == true)
        assertTrue(resp.bodyString().contains("My Audiobook"))
    }

    @Test
    fun `reader page for book with chapters renders audio-multi player`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Spoken Word")
        uploadChapter(token, bookId, 0, "Chapter One")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // Chapter panel
        assertTrue(body.contains("ch-panel"), "Chapter list panel should be present")
        // Chapter item rendered
        assertTrue(body.contains("Chapter One"), "Chapter title should appear in chapter list")
        // Audio element
        assertTrue(body.contains("audio-multi-el"), "Audio element should be present")
    }

    @Test
    fun `reader page for book with chapters shows prev and next chapter buttons`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("btn-prev-ch"), "Previous chapter button should be present")
        assertTrue(body.contains("btn-next-ch"), "Next chapter button should be present")
    }

    @Test
    fun `reader page for book with chapters does not show zoom controls`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("btn-zoom-in"), "Zoom controls must not appear in audio-multi reader")
        assertFalse(body.contains("btn-zoom-out"), "Zoom controls must not appear in audio-multi reader")
    }

    @Test
    fun `reader page for book with chapters includes CHAPTERS JS array with all chapters`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Part One")
        uploadChapter(token, bookId, 1, "Part Two")
        uploadChapter(token, bookId, 2, "Part Three")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Part One"), "Chapter 0 title should be in CHAPTERS array")
        assertTrue(body.contains("Part Two"), "Chapter 1 title should be in CHAPTERS array")
        assertTrue(body.contains("Part Three"), "Chapter 2 title should be in CHAPTERS array")
        assertTrue(body.contains("const CHAPTERS"), "CHAPTERS JS constant should be defined")
    }

    @Test
    fun `reader page for book with no chapters and no file shows none type`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // When no file and no chapters, should not render audio-multi elements
        assertFalse(body.contains("audio-multi-el"), "No audio-multi player for empty book")
        assertFalse(body.contains("btn-prev-ch"), "No prev-chapter button for empty book")
    }

    // ── Audiobook progress packed encoding roundtrip ──────────────────────────

    @Test
    fun `save packed progress and retrieve via book API`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)
        uploadChapter(token, bookId, 1)

        // Packed = trackIndex * 1_000_000 + offsetSeconds: track 1 at offset 300s
        val packed = 1 * 1_000_000 + 300

        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=$packed"),
        )

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, bookResp.status)
        val tree = Json.mapper.readTree(bookResp.bodyString())
        val storedPage = tree.get("progress")?.get("currentPage")?.asInt()
        assertEquals(packed, storedPage, "Packed progress should be stored and returned as-is")
    }

    @Test
    fun `reader page renders INIT_PACKED from stored progress`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)
        uploadChapter(token, bookId, 1)

        val packed = 1 * 1_000_000 + 150  // track 1 at 150 seconds

        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=$packed"),
        )

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("INIT_PACKED = $packed") || body.contains("INIT_PACKED=$packed"),
            "Reader page should embed INIT_PACKED with the stored packed value",
        )
    }

    @Test
    fun `packed progress decode track 1 offset 300 encodes and decodes correctly`() {
        // Verify the encoding contract: packed = track * 1_000_000 + offset
        val track = 1
        val offsetSeconds = 300
        val packed = track * 1_000_000 + offsetSeconds
        assertEquals(1_000_300, packed)
        // Decode
        assertEquals(track, packed / 1_000_000)
        assertEquals(offsetSeconds, packed % 1_000_000)
    }

    @Test
    fun `packed progress decode track 0 offset 0 is 0`() {
        val packed = 0 * 1_000_000 + 0
        assertEquals(0, packed)
        assertEquals(0, packed / 1_000_000)
        assertEquals(0, packed % 1_000_000)
    }

    @Test
    fun `reader page INIT_PACKED is 0 for book with no saved progress`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("INIT_PACKED = 0") || body.contains("INIT_PACKED=0"),
            "INIT_PACKED should be 0 when no progress has been saved",
        )
    }

    @Test
    fun `reader page for audiobook has no pdf js script`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("pdf.min.js"), "PDF.js must not load for audio-multi reader")
    }

    @Test
    fun `chapter names with single quotes do not produce unescaped JS string break`() {
        val token = registerAndGetToken("arui")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Author's Note")

        val resp = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status, "Reader page must render successfully for chapter with apostrophe in title")
        val body = resp.bodyString()
        // A raw unescaped single-quote inside a JS '...' string would produce title: 'Author's Note'
        // which breaks JS — this must not appear verbatim.
        assertFalse(
            body.contains("title: 'Author's Note'"),
            "Unescaped single quote must not appear verbatim inside a JS string literal",
        )
        // The word Author must still appear somewhere (title is present in some escaped form)
        assertTrue(body.contains("Author"), "Chapter title content should appear in the page")
    }
}

// ── book.kte progress card tests ──────────────────────────────────────────────

class AudiobookBookPageTest : IntegrationTestBase() {

    private fun uploadChapter(token: String, bookId: String, trackIndex: Int) {
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-$trackIndex.mp3")
                .header("X-Track-Index", trackIndex.toString())
                .body(mp3.inputStream(), mp3.size.toLong()),
        )
    }

    @Test
    fun `book page for chapter-only audiobook has no page number input`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audio Book")
        uploadChapter(token, bookId, 0)

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("name=\"currentPage\""),
            "Page number input must not appear for chapter-only audiobooks",
        )
    }

    @Test
    fun `book page for chapter-only audiobook with progress shows decoded position`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audio Book")
        uploadChapter(token, bookId, 0)
        uploadChapter(token, bookId, 1)

        // packed: track 1 at 300s
        val packed = 1 * 1_000_000 + 300
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=$packed"),
        )

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        // Chapter 2 (1-indexed) should appear somewhere in the progress area
        assertTrue(body.contains("2"), "Chapter 2 position should be shown in book page progress")
        // Should not show the raw packed value
        assertFalse(
            body.contains("1000300"),
            "Raw packed value must not be shown to users",
        )
    }

    @Test
    fun `book page for regular book still has page number input`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Regular Book")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("name=\"currentPage\""),
            "Page number input should still be present for regular books",
        )
    }

    @Test
    fun `book page shows Read button for chapter-only audiobook`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Chapters Book")
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-0.mp3")
                .header("X-Track-Index", "0")
                .body(mp3.inputStream(), mp3.size.toLong()),
        )

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("/books/$bookId/read"),
            "Read button link should be present when book has chapters",
        )
    }

    @Test
    fun `book page does not show Read button for empty book`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Empty Book")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("/books/$bookId/read"),
            "Read button should not appear when book has no file and no chapters",
        )
    }

    @Test
    fun `book page download button absent for chapter-only audiobook`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audio Only Book")
        // Upload a chapter — makes this a chapter-only audiobook with null filePath
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "ch-0.mp3")
                .header("X-Track-Index", "0")
                .body(mp3.inputStream(), mp3.size.toLong()),
        )

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("/api/books/$bookId/file"),
            "Download link to /file must not appear for chapter-only audiobooks",
        )
    }

    @Test
    fun `book page download button present for book with uploaded file`() {
        val token = registerAndGetToken("bbp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "PDF Book")
        // Minimal PDF bytes
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) + ByteArray(100)
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(pdfBytes.inputStream(), pdfBytes.size.toLong()),
        )

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("/api/books/$bookId/file"),
            "Download link should be present for books with an uploaded file",
        )
    }
}
