package org.runary.integration

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.runary.TestFixture
import org.runary.config.Json
import org.runary.models.LoginResponse
import org.runary.services.AdminService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AuthService
import org.runary.services.BookDropService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.ComicService
import org.runary.services.EpubMetadataService
import org.runary.services.ExportService
import org.runary.services.GoodreadsImportService
import org.runary.services.JwtService
import org.runary.services.LibraryService
import org.runary.services.MagicShelfService
import org.runary.services.PasswordResetService
import org.runary.services.PdfMetadataService
import org.runary.services.ReadingSessionService
import org.runary.services.SeedService
import org.runary.services.UserSettingsService
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
        app =
            buildTestApp(
                authService = authService,
                libraryService = libraryService,
                bookService = bookService,
                jwtService = jwtService,
                bookDropService = bookDropService,
            )
    }

    private fun registerAndGetToken(prefix: String = "drop"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
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
