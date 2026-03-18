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
import org.booktower.services.BookDropService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class BookDropIntegrationTest {
    @TempDir
    lateinit var tmp: Path

    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private lateinit var dropDir: File
    private lateinit var bookDropService: BookDropService
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        dropDir = tmp.resolve("bookdrop").toFile().also { it.mkdirs() }
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
        bookDropService = BookDropService(bookService, dropDir.absolutePath)
        app = buildTestApp(authService = authService, libraryService = libraryService, bookService = bookService, jwtService = jwtService, bookDropService = bookDropService)
    }

    private fun registerAndGetToken(prefix: String = "drop"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"password123"}"""),
            )
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): Pair<String, String> {
        val libDir = tmp.resolve("lib_${System.nanoTime()}").toFile().also { it.mkdirs() }
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"DropLib","path":"${libDir.absolutePath.replace("\\", "\\\\")}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(resp.bodyString())
                .get("id")
                .asText()
        return libId to libDir.absolutePath
    }

    @Test
    fun `GET api bookdrop returns empty list when drop folder is empty`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/bookdrop").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(
            0,
            Json.mapper
                .readTree(resp.bodyString())
                .get("files")
                .size(),
        )
    }

    @Test
    fun `GET api bookdrop lists files placed in the drop folder`() {
        File(dropDir, "Dune.epub").writeText("fake epub")
        File(dropDir, "Foundation.pdf").writeBytes(ByteArray(100))

        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/bookdrop").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val files = Json.mapper.readTree(resp.bodyString()).get("files")
        val filenames = (0 until files.size()).map { files[it].get("filename").asText() }
        assert(filenames.contains("Dune.epub")) { "Should include Dune.epub" }
        assert(filenames.contains("Foundation.pdf")) { "Should include Foundation.pdf" }
    }

    @Test
    fun `POST api bookdrop filename import moves file and creates book`() {
        val epubFile = File(dropDir, "Hyperion.epub").also { it.writeText("fake epub content") }
        val token = registerAndGetToken()
        val (libId, _) = createLibrary(token)

        val resp =
            app(
                Request(Method.POST, "/api/bookdrop/Hyperion.epub/import")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val bookId =
            Json.mapper
                .readTree(resp.bodyString())
                .get("bookId")
                .asText()
        assert(bookId.isNotBlank()) { "bookId should be returned" }
        // Source file should be gone from drop folder
        assert(!epubFile.exists()) { "File should be moved out of drop folder" }
    }

    @Test
    fun `DELETE api bookdrop filename discards the file`() {
        File(dropDir, "Discard.epub").writeText("to discard")

        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.DELETE, "/api/bookdrop/Discard.epub")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, resp.status)
        assert(!File(dropDir, "Discard.epub").exists()) { "File should be deleted" }
    }

    @Test
    fun `DELETE api bookdrop filename returns 404 when file does not exist`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.DELETE, "/api/bookdrop/nonexistent.epub")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET api bookdrop requires authentication`() {
        val resp = app(Request(Method.GET, "/api/bookdrop"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
