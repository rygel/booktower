package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse

/**
 * End-to-end test for public profile settings and public profile page.
 */
class PublicProfilePageTest : IntegrationTestBase() {
    @Test
    fun `profile page has public profile toggle`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/profile").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("public-profile-section"), "Profile should have public profile section")
        assertTrue(html.contains("public-toggle"), "Should have toggle checkbox")
    }

    @Test
    fun `public profile API toggle works`() {
        val token = registerAndGetToken()
        // Enable
        val enableResp =
            app(
                Request(Method.PUT, "/api/user/profile/public")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"public":true}"""),
            )
        assertEquals(Status.NO_CONTENT, enableResp.status)

        // Check
        val statusResp =
            app(Request(Method.GET, "/api/user/profile/public").header("Cookie", "token=$token"))
        assertEquals(Status.OK, statusResp.status)
        assertTrue(statusResp.bodyString().contains("true"))
    }

    @Test
    fun `public profile returns data when enabled`() {
        val username = "pub_${System.nanoTime()}"
        val regResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val token = Json.mapper.readValue(regResp.bodyString(), LoginResponse::class.java).token

        // Enable public profile
        app(
            Request(Method.PUT, "/api/user/profile/public")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"public":true}"""),
        )

        // Access public profile (no auth needed)
        val profileResp = app(Request(Method.GET, "/api/profile/$username"))
        assertEquals(Status.OK, profileResp.status)
        val body = profileResp.bodyString()
        assertTrue(body.contains(username), "Public profile should contain username")
        assertTrue(body.contains("booksFinished"), "Should contain reading stats")
    }

    @Test
    fun `public profile returns 404 when disabled`() {
        val username = "priv_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        // Don't enable — profile should be private by default
        val resp = app(Request(Method.GET, "/api/profile/$username"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `public profile returns 404 for nonexistent user`() {
        val resp = app(Request(Method.GET, "/api/profile/nonexistent_user_12345"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }
}
