package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for duplicate detection and merge UI in admin panel.
 */
class AdminDuplicatesPageTest : IntegrationTestBase() {
    private fun registerAdmin(): String {
        val username = "admin_${System.nanoTime()}"
        val regResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
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
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `admin page has duplicates section`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("duplicates-section"), "Admin page should have duplicates section")
        assertTrue(html.contains("dup-scan-btn"), "Should have scan button")
    }

    @Test
    fun `duplicate scan API returns results`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/api/admin/duplicates").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val body = resp.bodyString()
        assertTrue(body.startsWith("["), "Should return JSON array")
    }

    @Test
    fun `admin page has merge JavaScript function`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("mergeDup"), "Should have mergeDup function")
    }

    @Test
    fun `duplicate scan requires admin`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/admin/duplicates").header("Cookie", "token=$token"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
