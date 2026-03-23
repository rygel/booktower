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
 * End-to-end test for admin backup/restore and metadata refresh UI.
 */
class AdminBackupPageTest : IntegrationTestBase() {
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
        // Re-login to get admin JWT
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `admin page has backup section`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("backup-section"), "Admin page should have backup section")
        assertTrue(html.contains("/api/admin/backup"), "Should have backup download link")
        assertTrue(html.contains("backup-file"), "Should have file upload input")
    }

    @Test
    fun `admin page has metadata refresh button`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("metadata-refresh-btn"), "Should have metadata refresh button")
    }

    @Test
    fun `backup export returns JSON download`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/api/admin/backup").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("application/json") == true)
        assertTrue(resp.header("Content-Disposition")?.contains("attachment") == true)
        val body = resp.bodyString()
        assertTrue(body.contains("metadata"), "Backup should contain metadata")
        assertTrue(body.contains("tables"), "Backup should contain tables")
    }

    @Test
    fun `admin page requires admin role`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/admin").header("Cookie", "token=$token"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
