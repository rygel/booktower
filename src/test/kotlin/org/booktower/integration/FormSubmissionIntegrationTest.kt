package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.cookies
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormSubmissionIntegrationTest : IntegrationTestBase() {

    private fun uniqueUser() = "formuser_${System.nanoTime()}"

    @Test
    fun `register via form submission redirects to index`() {
        val username = uniqueUser()
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("email", "$username@test.com")
                .form("password", "password123"),
        )

        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/", response.header("Location"))
        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie, "Auth cookie should be set on register")
        assertTrue(cookie.value.isNotBlank())
    }

    @Test
    fun `login via form submission redirects to index`() {
        val username = uniqueUser()
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("email", "$username@test.com")
                .form("password", "password123"),
        )

        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("password", "password123"),
        )

        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/", response.header("Location"))
        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie, "Auth cookie should be set on login")
    }

    @Test
    fun `login via form with wrong password returns 401`() {
        val username = uniqueUser()
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("email", "$username@test.com")
                .form("password", "password123"),
        )

        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("password", "wrongpassword"),
        )

        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `register via form with missing username returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("email", "x@test.com")
                .form("password", "password123"),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `login via form with missing fields returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", "someone"),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register via JSON still works`() {
        val username = uniqueUser()
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
    }

    @Test
    fun `login via JSON still works`() {
        val username = uniqueUser()
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `full browser flow - register via form then access authenticated page`() {
        val username = uniqueUser()

        val registerResponse = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("email", "$username@test.com")
                .form("password", "password123"),
        )
        val token = registerResponse.cookies().find { it.name == "token" }!!.value

        val indexResponse = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, indexResponse.status)
        assertTrue(indexResponse.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `full browser flow - register then create library then see it on index`() {
        val username = uniqueUser()

        val registerResponse = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .form("username", username)
                .form("email", "$username@test.com")
                .form("password", "password123"),
        )
        val token = registerResponse.cookies().find { it.name == "token" }!!.value

        app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Browser Flow Lib","path":"./data/test-bf-${System.nanoTime()}"}"""),
        )

        val indexResponse = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertTrue(indexResponse.bodyString().contains("Browser Flow Lib"))
    }

    @Test
    fun `login form in template has username field`() {
        val response = app(Request(Method.GET, "/login"))
        val body = response.bodyString()
        assertTrue(body.contains("name=\"username\""), "Login form should have username field")
        assertTrue(body.contains("name=\"password\""), "Login form should have password field")
    }

    @Test
    fun `register form in template has username and email fields`() {
        val response = app(Request(Method.GET, "/register"))
        val body = response.bodyString()
        assertTrue(body.contains("name=\"username\""), "Register form should have username field")
        assertTrue(body.contains("name=\"email\""), "Register form should have email field")
        assertTrue(body.contains("name=\"password\""), "Register form should have password field")
    }
}
