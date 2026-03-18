package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.AuthorInfo
import org.booktower.services.AuthorMetadataService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
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
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthorMetadataIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()

    // Stub that returns canned data without making real HTTP calls
    private val stubAuthorService =
        object : AuthorMetadataService() {
            override fun fetch(name: String): AuthorInfo? =
                when {
                    name.equals("Frank Herbert", ignoreCase = true) ->
                        AuthorInfo(
                            name = "Frank Herbert",
                            bio = "American science fiction author best known for Dune.",
                            photoUrl = "https://covers.openlibrary.org/a/olid/OL26320A-L.jpg",
                            birthDate = "1920-10-08",
                            deathDate = "1986-02-11",
                            workCount = 42,
                            topWork = "Dune",
                        )
                    name.equals("Unknown Author", ignoreCase = true) -> null
                    else -> null
                }
        }

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
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
        val metadataFetchService = MetadataFetchService()
        val magicShelfService = MagicShelfService(jdbi, bookService)
        val passwordResetService = PasswordResetService(jdbi)
        val apiTokenService = ApiTokenService(jdbi)
        val exportService = ExportService(jdbi)
        val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
        val comicService = ComicService()
        val goodreadsImportService = GoodreadsImportService(bookService)
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath)
        return buildTestApp(
            authService = authService,
            libraryService = libraryService,
            bookService = bookService,
            jwtService = jwtService,
            metadataFetchService = metadataFetchService,
            authorMetadataService = stubAuthorService,
        )
    }

    private fun registerAndGetToken(): String {
        val u = "author_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"password123"}"""),
            )
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `GET api authors name metadata returns author info`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/authors/Frank%20Herbert/metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Frank Herbert", tree.get("name").asText())
        assertEquals("Dune", tree.get("topWork").asText())
        assertEquals("1920-10-08", tree.get("birthDate").asText())
        assertEquals("1986-02-11", tree.get("deathDate").asText())
        assertEquals(42, tree.get("workCount").asInt())
    }

    @Test
    fun `GET api authors name metadata includes bio and photoUrl`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/authors/Frank%20Herbert/metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assert(tree.get("bio").asText().isNotBlank()) { "bio should be non-blank" }
        assert(tree.get("photoUrl").asText().startsWith("https://")) { "photoUrl should be a URL" }
    }

    @Test
    fun `GET api authors name metadata returns 404 for unknown author`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/authors/Unknown%20Author/metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET api authors name metadata requires authentication`() {
        val resp = app(Request(Method.GET, "/api/authors/Frank%20Herbert/metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
