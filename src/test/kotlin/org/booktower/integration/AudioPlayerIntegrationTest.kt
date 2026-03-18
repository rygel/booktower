package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPlayerIntegrationTest : IntegrationTestBase() {
    private fun uploadFakeAudio(
        token: String,
        bookId: String,
        ext: String = "mp3",
    ) {
        // Minimal fake bytes — enough to test upload path; metadata extraction is async
        val fakeBytes = ByteArray(64) { it.toByte() }
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "audiobook.$ext")
                .body(fakeBytes.inputStream()),
        )
    }

    @Test
    fun `reader page renders audio player for mp3 book`() {
        val token = registerAndGetToken("ap1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audio Book MP3")
        uploadFakeAudio(token, bookId, "mp3")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // Check for the HTML element attribute, not CSS selector
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for mp3 book",
        )
    }

    @Test
    fun `reader page renders audio player for m4b book`() {
        val token = registerAndGetToken("ap2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook M4B")
        uploadFakeAudio(token, bookId, "m4b")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for m4b book",
        )
    }

    @Test
    fun `reader page renders audio player for ogg book`() {
        val token = registerAndGetToken("ap3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook OGG")
        uploadFakeAudio(token, bookId, "ogg")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for ogg book",
        )
    }

    @Test
    fun `reader page renders audio player for m4a book`() {
        val token = registerAndGetToken("ap_m4a")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook M4A")
        uploadFakeAudio(token, bookId, "m4a")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for m4a book",
        )
    }

    @Test
    fun `reader page renders audio player for flac book`() {
        val token = registerAndGetToken("ap_flac")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook FLAC")
        uploadFakeAudio(token, bookId, "flac")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for flac book",
        )
    }

    @Test
    fun `reader page renders audio player for aac book`() {
        val token = registerAndGetToken("ap_aac")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Audiobook AAC")
        uploadFakeAudio(token, bookId, "aac")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("id=\"audio-el\""),
            "Audio player element must be present for aac book",
        )
    }

    @Test
    fun `reader page shows speed controls for audio book`() {
        val token = registerAndGetToken("ap4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Speed Test Book")
        uploadFakeAudio(token, bookId, "mp3")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // data-rate only appears as HTML attribute, not in CSS
        assertTrue(
            body.contains("data-rate"),
            "Speed control buttons must be present for audio book",
        )
    }

    @Test
    fun `reader page does not show PDF canvas for audio book`() {
        val token = registerAndGetToken("ap5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Not A PDF")
        uploadFakeAudio(token, bookId, "mp3")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // id="pdf-canvas" as HTML attribute only appears inside the PDF @if block
        assertFalse(
            body.contains("id=\"pdf-canvas\""),
            "PDF canvas element must not be rendered for audio book",
        )
    }

    @Test
    fun `reader page does not show audio player for PDF book`() {
        val token = registerAndGetToken("ap6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "A PDF Book")
        val fakePdfBytes = ByteArray(64) { it.toByte() }
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.pdf")
                .body(fakePdfBytes.inputStream()),
        )

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // id="audio-el" only appears inside the audio @elseif block
        assertFalse(
            body.contains("id=\"audio-el\""),
            "Audio player must not appear for PDF book",
        )
        assertTrue(
            body.contains("id=\"pdf-canvas\""),
            "PDF canvas must appear for PDF book",
        )
    }

    @Test
    fun `audio stream endpoint returns 200 or 206 for existing file`() {
        val token = registerAndGetToken("ap7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Streamable Book")
        uploadFakeAudio(token, bookId, "mp3")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/audio")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(
            response.status.code in listOf(200, 206),
            "Audio stream must return 200 or 206 for a book with an uploaded file",
        )
    }

    @Test
    fun `audio stream endpoint returns 404 when no file uploaded`() {
        val token = registerAndGetToken("ap8")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "No File Book")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/audio")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(
            Status.NOT_FOUND,
            response.status,
            "Audio stream must return 404 when no file is uploaded",
        )
    }

    @Test
    fun `reader page for single-file audio book includes getBookmarkPage and jumpToPage functions`() {
        val token = registerAndGetToken("ap_bm")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Single Audio Bookmark Book")
        uploadFakeAudio(token, bookId, "mp3")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("getBookmarkPage"), "Reader page must define getBookmarkPage for single-file audio book")
        assertTrue(body.contains("jumpToPage"), "Reader page must define jumpToPage for single-file audio book")
    }

    @Test
    fun `reader page for single-file audio book includes bookmark add button`() {
        val token = registerAndGetToken("ap_bm2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Single Audio Bookmark Panel Book")
        uploadFakeAudio(token, bookId, "mp3")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("btn-add-bookmark"), "Bookmark add button must be present in single-file audio reader")
    }

    @Test
    fun `audio stream endpoint requires authentication`() {
        val response = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000001/audio"))
        assertTrue(
            response.status.code in listOf(302, 303, 401),
            "Audio stream must require authentication",
        )
    }

    @Test
    fun `audio stream responds to Range header with 206`() {
        val token = registerAndGetToken("ap10")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Range Stream Book")

        val fakeBytes = ByteArray(64) { it.toByte() }
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "audio.mp3")
                .body(fakeBytes.inputStream()),
        )

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/audio")
                    .header("Cookie", "token=$token")
                    .header("Range", "bytes=0-7"),
            )
        assertEquals(
            Status.PARTIAL_CONTENT,
            response.status,
            "Audio stream must return 206 Partial Content for Range request",
        )
        assertTrue(
            response.header("Content-Range")?.startsWith("bytes 0-7/") == true,
            "Content-Range header must be returned for Range request",
        )
    }
}
