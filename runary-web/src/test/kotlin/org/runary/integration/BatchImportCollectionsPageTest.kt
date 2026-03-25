package org.runary.integration

import org.runary.config.Json
import org.runary.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for batch import UI in admin and collections page.
 */
class BatchImportCollectionsPageTest : IntegrationTestBase() {
    private fun registerAdmin(): String {
        val username = "admin_${System.nanoTime()}"
        val regResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val userId =
            Json.mapper
                .readValue(regResp.bodyString(), LoginResponse::class.java)
                .user.id
        promoteToAdmin(userId)
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    // ── Batch Import ──────────────────────────────────────────────────────

    @Test
    fun `admin page has batch import section`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("batch-import-section"), "Admin should have batch import section")
        assertTrue(html.contains("import-path"), "Should have path input")
        assertTrue(html.contains("import-btn"), "Should have import button")
    }

    // ── Collections ───────────────────────────────────────────────────────

    @Test
    fun `collections page requires authentication`() {
        val resp = app(Request(Method.GET, "/collections"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `collections page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/collections").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-coll-btn"), "Should have create button")
    }

    @Test
    fun `collections page shows created collection`() {
        val token = registerAndGetToken()
        app(
            Request(Method.POST, "/api/collections")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("name=My%20Fiction&description=Fiction%20books"),
        )

        val resp = app(Request(Method.GET, "/collections").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("My Fiction"), "Should show collection name")
        assertTrue(html.contains("Fiction books"), "Should show description")
    }

    @Test
    fun `sidebar contains collections link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/collections\""), "Sidebar should have /collections link")
    }
}
