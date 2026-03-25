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
import org.runary.config.SmtpConfig
import org.runary.models.LoginResponse
import org.runary.services.AdminService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AuthService
import org.runary.services.BookDeliveryService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.ComicService
import org.runary.services.EmailService
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

class BookDeliveryIntegrationTest {
    @TempDir
    lateinit var tmp: Path

    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()

    // Track sent emails in-memory for assertions; real SMTP is disabled
    private val sentEmails = mutableListOf<Triple<String, String, String>>() // to, title, filename

    private val emailService =
        object : EmailService(SmtpConfig("", 587, "", "", "", false)) {
            override fun sendBook(
                toEmail: String,
                bookTitle: String,
                filename: String,
                fileBytes: ByteArray,
            ) {
                sentEmails += Triple(toEmail, bookTitle, filename)
            }
        }

    private lateinit var app: HttpHandler
    private lateinit var booksDir: File

    @BeforeEach
    fun setup() {
        sentEmails.clear()
        booksDir = tmp.resolve("books").toFile().also { it.mkdirs() }
        app = buildApp()
    }

    private fun buildApp(): HttpHandler {
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
        val deliveryService = BookDeliveryService(jdbi, bookService, emailService, booksDir.absolutePath)
        return buildTestApp(
            authService = authService,
            libraryService = libraryService,
            bookService = bookService,
            jwtService = jwtService,
            bookDeliveryService = deliveryService,
        )
    }

    private fun registerAndGetToken(prefix: String = "del"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibraryAndBook(
        token: String,
        bookFile: File? = null,
    ): Pair<String, String> {
        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"DelLib ${System.nanoTime()}","path":"./data/del-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Dune","author":"Frank Herbert","description":null,"libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()
        // Optionally point the book at a real file.
        // Raw SQL is acceptable here: file_path is internal state set during library scanning,
        // and there is no BookService method to update just file_path on an existing book.
        if (bookFile != null) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                    .bind(0, bookFile.absolutePath)
                    .bind(1, bookId)
                    .execute()
            }
        }
        return libId to bookId
    }

    // ── Recipients ─────────────────────────────────────────────────────────────

    @Test
    fun `POST api delivery recipients adds recipient and GET returns it`() {
        val token = registerAndGetToken()

        val addResp =
            app(
                Request(Method.POST, "/api/delivery/recipients")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"label":"My Kindle","email":"mykindle@kindle.com"}"""),
            )
        assertEquals(Status.CREATED, addResp.status)
        val created = Json.mapper.readTree(addResp.bodyString())
        assertEquals("My Kindle", created.get("label").asText())
        assertEquals("mykindle@kindle.com", created.get("email").asText())

        val listResp = app(Request(Method.GET, "/api/delivery/recipients").header("Cookie", "token=$token"))
        assertEquals(Status.OK, listResp.status)
        val list = Json.mapper.readTree(listResp.bodyString()).get("recipients")
        assert(list.any { it.get("email").asText() == "mykindle@kindle.com" })
    }

    @Test
    fun `DELETE api delivery recipients id removes recipient`() {
        val token = registerAndGetToken()
        val addResp =
            app(
                Request(Method.POST, "/api/delivery/recipients")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"label":"Temp","email":"temp@kindle.com"}"""),
            )
        val recipientId =
            Json.mapper
                .readTree(addResp.bodyString())
                .get("id")
                .asText()

        val delResp = app(Request(Method.DELETE, "/api/delivery/recipients/$recipientId").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp = app(Request(Method.GET, "/api/delivery/recipients").header("Cookie", "token=$token"))
        val list = Json.mapper.readTree(listResp.bodyString()).get("recipients")
        assert(list.none { it.get("id").asText() == recipientId })
    }

    @Test
    fun `POST api books id send delivers book by email`() {
        val token = registerAndGetToken()
        val bookFile = tmp.resolve("Dune.epub").toFile().also { it.writeText("fake epub content") }
        val (_, bookId) = createLibraryAndBook(token, bookFile)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/send")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"user@kindle.com"}"""),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals(
            true,
            Json.mapper
                .readTree(resp.bodyString())
                .get("sent")
                .asBoolean(),
        )
        assertEquals(1, sentEmails.size)
        assertEquals("user@kindle.com", sentEmails[0].first)
        assertEquals("Dune", sentEmails[0].second)
    }

    @Test
    fun `POST api books id send returns 400 when book has no file`() {
        val token = registerAndGetToken()
        val (_, bookId) = createLibraryAndBook(token) // no file attached

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/send")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"user@kindle.com"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST api books id send returns 401 without authentication`() {
        val resp =
            app(
                Request(Method.POST, "/api/books/some-id/send")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"x@y.com"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `recipient isolation - users cannot see each other recipients`() {
        val token1 = registerAndGetToken("del1")
        val token2 = registerAndGetToken("del2")
        app(
            Request(Method.POST, "/api/delivery/recipients")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"label":"User1 Kindle","email":"user1@kindle.com"}"""),
        )
        val listResp = app(Request(Method.GET, "/api/delivery/recipients").header("Cookie", "token=$token2"))
        val list = Json.mapper.readTree(listResp.bodyString()).get("recipients")
        assert(list.none { it.get("email").asText() == "user1@kindle.com" })
    }
}
