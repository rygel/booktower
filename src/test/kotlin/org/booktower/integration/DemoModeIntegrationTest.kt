package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoModeIntegrationTest : IntegrationTestBase() {

    private val demoApp by lazy { buildApp(demoMode = true) }

    @Test
    fun `GET demo status returns demoMode=false by default`() {
        val resp = app(Request(Method.GET, "/api/demo/status"))
        assertEquals(Status.OK, resp.status)
        assertFalse(Json.mapper.readTree(resp.bodyString()).get("demoMode").asBoolean())
    }

    @Test
    fun `GET demo status returns demoMode=true on demo app`() {
        val resp = demoApp(Request(Method.GET, "/api/demo/status"))
        assertEquals(Status.OK, resp.status)
        assertTrue(Json.mapper.readTree(resp.bodyString()).get("demoMode").asBoolean())
    }

    @Test
    fun `in demo mode POST to API returns 403`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp = demoApp(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Test","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        assertEquals(Status.FORBIDDEN, resp.status)
        assertTrue(resp.bodyString().contains("DEMO_MODE"))
    }

    @Test
    fun `in demo mode PUT to API returns 403`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp = demoApp(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Updated","author":null,"description":null}"""),
        )
        assertEquals(Status.FORBIDDEN, resp.status)
    }

    @Test
    fun `in demo mode DELETE to API returns 403`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp = demoApp(
            Request(Method.DELETE, "/api/libraries/$libId")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.FORBIDDEN, resp.status)
    }

    @Test
    fun `in demo mode GET requests still work`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp = demoApp(
            Request(Method.GET, "/api/libraries")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `in demo mode auth login still works`() {
        // Register via the normal app (POST /auth/register), then login via demoApp
        val username = "demouser_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val loginResp = demoApp(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )
        assertEquals(Status.OK, loginResp.status)
    }

    @Test
    fun `without demo mode all mutations work normally`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val resp = app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Normal Book","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        assertEquals(Status.CREATED, resp.status)
    }
}
