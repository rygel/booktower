package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.config.WeblateConfig
import org.booktower.handlers.AppHandler
import org.booktower.models.BookDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestBase {
    protected lateinit var app: HttpHandler

    @BeforeEach
    fun setupApp() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, config.storage, pdfMetadataService)
        val bookService = BookService(jdbi, config.storage)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val appHandler = AppHandler(
            authService, libraryService, bookService, bookmarkService,
            userSettingsService, pdfMetadataService, jwtService, config.storage, TemplateRenderer(),
            WeblateHandler(WeblateConfig("", "", "", false)),
        )
        app = GlobalErrorFilter().then(appHandler.routes())
    }

    protected fun registerAndGetToken(prefix: String = "test"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    protected fun createLibrary(token: String, nameSuffix: String = ""): String {
        val name = if (nameSuffix.isNotBlank()) nameSuffix else "Lib ${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"$name","path":"./data/test-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    protected fun createBook(token: String, libId: String, title: String = "Book ${System.nanoTime()}"): String {
        val response = app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"$title","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), BookDto::class.java).id
    }
}
