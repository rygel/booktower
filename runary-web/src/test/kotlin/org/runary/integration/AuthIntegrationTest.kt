package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "user_${System.nanoTime()}"

    private fun registerJson(
        username: String,
        email: String = "$username@test.com",
        password: String = org.runary.TestPasswords.DEFAULT,
    ): String = """{"username":"$username","email":"$email","password":"$password"}"""

    private fun loginJson(
        username: String,
        password: String = org.runary.TestPasswords.DEFAULT,
    ): String = """{"username":"$username","password":"$password"}"""

    @Test
    fun `register returns 201 with token and user data`() {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(username)),
            )

        assertEquals(Status.CREATED, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
        assertTrue(body.token.isNotBlank())
        assertNotNull(response.cookies().find { it.name == "token" })
        assertEquals(body.token, response.cookies().find { it.name == "token" }?.value)
    }

    @Test
    fun `register sets httpOnly cookie`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(uniqueUser())),
            )

        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie)
        assertTrue(cookie.httpOnly)
    }

    @Test
    fun `register with duplicate username returns error`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(username, "${username}_2@test.com")),
            )

        assertTrue(response.status.code >= 400, "Expected error status but got ${response.status}")
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `register with empty body returns 400`() {
        val response = app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(""))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with short username returns 400`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"ab","email":"ab@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("VALIDATION_ERROR"))
    }

    @Test
    fun `register with short password returns 400`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"${uniqueUser()}","email":"x@test.com","password":"short"}"""),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with invalid email returns 400`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"${uniqueUser()}","email":"not-an-email","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with special chars in username returns 400`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"user@name!","email":"x@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `login with correct credentials returns 200 with token`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username)),
            )

        assertEquals(Status.OK, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
        assertTrue(body.token.isNotBlank())
        assertNotNull(response.cookies().find { it.name == "token" })
    }

    @Test
    fun `login with wrong password returns 401`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username, "wrongpassword")),
            )

        assertEquals(Status.UNAUTHORIZED, response.status)
        assertTrue(response.bodyString().contains("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login with non-existent user returns 401`() {
        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson("nonexistent_${System.nanoTime()}")),
            )

        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `login with empty body returns 400`() {
        val response = app(Request(Method.POST, "/auth/login").header("Content-Type", "application/json").body(""))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `logout clears token cookie`() {
        val response = app(Request(Method.POST, "/auth/logout"))

        // logout redirects to /login (or returns 200 for API clients)
        assertTrue(response.status == Status.OK || response.status == Status.SEE_OTHER)
        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie)
        assertEquals("", cookie.value)
        assertEquals(0L, cookie.maxAge)
    }

    @Test
    fun `login with email address via JSON succeeds`() {
        val username = uniqueUser()
        val email = "$username@test.com"
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username, email)))

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$email","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.OK, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
    }

    @Test
    fun `login with email address via form succeeds`() {
        val username = uniqueUser()
        val email = "$username@test.com"
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username, email)))

        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("username=${email.replace("@", "%40")}&password=${org.runary.TestPasswords.DEFAULT}"),
            )
        assertEquals(Status.SEE_OTHER, response.status)
        assertNotNull(response.cookies().find { it.name == "token" && it.value.isNotBlank() })
    }

    @Test
    fun `register then login produces valid tokens for API access`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val loginResponse =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username)),
            )
        val token = loginResponse.cookies().find { it.name == "token" }!!.value

        val librariesResponse =
            app(
                Request(Method.GET, "/api/libraries").header("Cookie", "token=$token"),
            )

        assertEquals(Status.OK, librariesResponse.status)
    }

    @Test
    fun `register returns 403 when registration is closed`() {
        val closedApp = buildApp(registrationOpen = false)
        val response =
            closedApp(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(uniqueUser())),
            )
        assertEquals(Status.FORBIDDEN, response.status)
        assertTrue(response.bodyString().contains("REGISTRATION_CLOSED"))
    }

    @Test
    fun `form register returns 403 when registration is closed`() {
        val closedApp = buildApp(registrationOpen = false)
        val username = uniqueUser()
        val response =
            closedApp(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("username=$username&email=$username%40test.com&password=${org.runary.TestPasswords.DEFAULT}"),
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `GET register redirects to login when registration is closed`() {
        val closedApp = buildApp(registrationOpen = false)
        val response = closedApp(Request(Method.GET, "/register"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/login", response.header("Location"))
    }

    @Test
    fun `landing page hides sign-up link when registration is closed`() {
        val closedApp = buildApp(registrationOpen = false)
        val response = closedApp(Request(Method.GET, "/"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("action.signin") || body.contains("Sign in") || body.contains("Login"))
        assertTrue(!body.contains("/register"))
    }
}
