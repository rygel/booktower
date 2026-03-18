package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
import org.booktower.filters.globalErrorFilter
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.FontService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReaderPreferencesService
import org.booktower.services.ReadingSessionService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FontIntegrationTest {
    @TempDir
    lateinit var fontsDir: Path

    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, pdfMetadataService)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        val readingSessionService = ReadingSessionService(jdbi)
        val bookService = BookService(jdbi, analyticsService, readingSessionService)
        val adminService = AdminService(jdbi)
        val annotationService = AnnotationService(jdbi)
        val magicShelfService = MagicShelfService(jdbi, bookService)
        val passwordResetService = PasswordResetService(jdbi)
        val apiTokenService = ApiTokenService(jdbi)
        val exportService = ExportService(jdbi)
        val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
        val comicService = ComicService()
        val goodreadsImportService = GoodreadsImportService(bookService)
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath)
        val fontService = FontService(jdbi, fontsDir.toString())
        val readerPreferencesService = ReaderPreferencesService(userSettingsService)
        app = buildTestApp(authService = authService, libraryService = libraryService, bookService = bookService, jwtService = jwtService, fontService = fontService, readerPreferencesService = readerPreferencesService)
    }

    private fun registerAndToken(): String {
        val u = "font_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        return Json.mapper.readValue(r.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `GET api fonts returns empty list initially`() {
        val token = registerAndToken()
        val resp = app(Request(Method.GET, "/api/fonts").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray && arr.size() == 0)
    }

    @Test
    fun `POST api fonts uploads a font file`() {
        val token = registerAndToken()
        val fakeFont = ByteArray(128) { it.toByte() }
        val resp =
            app(
                Request(Method.POST, "/api/fonts")
                    .header("Cookie", "token=$token")
                    .header("Content-Disposition", """attachment; filename="MyFont.ttf"""")
                    .body(fakeFont.inputStream()),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("id")?.asText()?.isNotBlank() == true)
        assertEquals("MyFont.ttf", tree.get("originalName")?.asText())
        assertTrue(tree.get("url")?.asText()?.contains("MyFont") == true)
    }

    @Test
    fun `GET api fonts lists uploaded fonts`() {
        val token = registerAndToken()
        val fakeFont = ByteArray(64) { 0x41 }
        app(
            Request(Method.POST, "/api/fonts")
                .header("Cookie", "token=$token")
                .header("Content-Disposition", """attachment; filename="TestFont.otf"""")
                .body(fakeFont.inputStream()),
        )
        val resp = app(Request(Method.GET, "/api/fonts").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray && arr.size() >= 1)
        assertEquals("TestFont.otf", arr[0].get("originalName")?.asText())
    }

    @Test
    fun `DELETE api fonts removes the font`() {
        val token = registerAndToken()
        val fakeFont = ByteArray(64) { 0x42 }
        val upload =
            app(
                Request(Method.POST, "/api/fonts")
                    .header("Cookie", "token=$token")
                    .header("Content-Disposition", """attachment; filename="ToDelete.woff"""")
                    .body(fakeFont.inputStream()),
            )
        val fontId =
            Json.mapper
                .readTree(upload.bodyString())
                .get("id")
                .asText()

        val del = app(Request(Method.DELETE, "/api/fonts/$fontId").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, del.status)

        val list = app(Request(Method.GET, "/api/fonts").header("Cookie", "token=$token"))
        val arr = Json.mapper.readTree(list.bodyString())
        assertTrue(arr.none { it.get("id")?.asText() == fontId })
    }

    @Test
    fun `POST api fonts rejects unsupported extension`() {
        val token = registerAndToken()
        val resp =
            app(
                Request(Method.POST, "/api/fonts")
                    .header("Cookie", "token=$token")
                    .header("Content-Disposition", """attachment; filename="script.exe"""")
                    .body(ByteArray(64).inputStream()),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `GET fonts userId filename serves the font file`() {
        val token = registerAndToken()
        val fakeFont = ByteArray(100) { 0x50 }
        val upload =
            app(
                Request(Method.POST, "/api/fonts")
                    .header("Cookie", "token=$token")
                    .header("Content-Disposition", """attachment; filename="Serve.ttf"""")
                    .body(fakeFont.inputStream()),
            )
        val url =
            Json.mapper
                .readTree(upload.bodyString())
                .get("url")
                .asText()
        val resp = app(Request(Method.GET, url))
        assertEquals(Status.OK, resp.status)
        assertEquals("font/ttf", resp.header("Content-Type"))
    }

    @Test
    fun `font endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/fonts"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
