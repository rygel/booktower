package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserPermissionsIntegrationTest : IntegrationTestBase() {
    private fun registerAdminAndGetToken(): Pair<String, String> {
        val u = "padmin_${System.nanoTime()}"
        val regResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        val userId =
            Json.mapper
                .readValue(regResp.bodyString(), LoginResponse::class.java)
                .user.id
        TestFixture.database.getJdbi().useHandle<Exception> { h ->
            h.createUpdate("UPDATE users SET is_admin = true WHERE id = ?").bind(0, userId).execute()
        }
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","password":"pass1234"}"""),
            )
        return userId to Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    private fun registerUserAndGetId(): String {
        val u = "puser_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        return Json.mapper
            .readValue(r.bodyString(), LoginResponse::class.java)
            .user.id
    }

    @Test
    fun `GET admin permissions returns default permissions for a user`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val userId = registerUserAndGetId()

        val resp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/permissions")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        // Defaults
        assertTrue(tree.get("canManageLibraries")?.asBoolean() == true)
        assertTrue(tree.get("canUploadBooks")?.asBoolean() == true)
        assertFalse(tree.get("canDeleteBooks")?.asBoolean() ?: true)
        assertFalse(tree.get("canAccessAdminPanel")?.asBoolean() ?: true)
    }

    @Test
    fun `PUT admin permissions updates user permissions`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val userId = registerUserAndGetId()

        val payload = """{"userId":"$userId","canDeleteBooks":true,"canManageLibraries":true,
            "canUploadBooks":true,"canDownloadBooks":true,"canEditMetadata":true,
            "canManageBookmarks":true,"canManageAnnotations":true,"canManageReadingProgress":true,
            "canManageShelves":true,"canExportBooks":true,"canSendToDevice":true,
            "canUseKoboSync":true,"canUseKoreaderSync":true,"canUseOpds":true,
            "canUseApiTokens":true,"canManageJournal":true,"canManageReadingSessions":true,
            "canViewStats":true,"canEditProfile":true,"canChangePassword":true,
            "canChangeEmail":true,"canUseSearchFilters":true,"canViewAuditLog":false,
            "canManageNotebooks":true,"canManageFonts":true,
            "canAccessAdminPanel":false}"""

        val resp =
            app(
                Request(Method.PUT, "/api/admin/users/$userId/permissions")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body(payload),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("canDeleteBooks")?.asBoolean() == true)
    }

    @Test
    fun `GET and PUT permissions are consistent — saved values are returned`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val userId = registerUserAndGetId()

        // Disable download and kobo sync
        val payload = """{"userId":"$userId","canManageLibraries":true,"canUploadBooks":true,
            "canDownloadBooks":false,"canDeleteBooks":false,"canEditMetadata":true,
            "canManageBookmarks":true,"canManageAnnotations":true,"canManageReadingProgress":true,
            "canManageShelves":true,"canExportBooks":true,"canSendToDevice":true,
            "canUseKoboSync":false,"canUseKoreaderSync":true,"canUseOpds":true,
            "canUseApiTokens":true,"canManageJournal":true,"canManageReadingSessions":true,
            "canViewStats":true,"canEditProfile":true,"canChangePassword":true,
            "canChangeEmail":true,"canUseSearchFilters":true,"canViewAuditLog":false,
            "canManageNotebooks":true,"canManageFonts":true,
            "canAccessAdminPanel":false}"""
        app(
            Request(Method.PUT, "/api/admin/users/$userId/permissions")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body(payload),
        )

        val getResp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/permissions")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, getResp.status)
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertFalse(tree.get("canDownloadBooks")?.asBoolean() ?: true)
        assertFalse(tree.get("canUseKoboSync")?.asBoolean() ?: true)
        assertTrue(tree.get("canManageLibraries")?.asBoolean() == true)
    }

    @Test
    fun `non-admin cannot access permission endpoints`() {
        val token = registerAndGetToken("noadmin")
        val userId = registerUserAndGetId()

        val resp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/permissions")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, resp.status)
    }

    @Test
    fun `unauthenticated request to permissions returns 401`() {
        val userId = registerUserAndGetId()
        val resp = app(Request(Method.GET, "/api/admin/users/$userId/permissions"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `userId is reflected in permissions response`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val userId = registerUserAndGetId()

        val resp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/permissions")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(userId, tree.get("userId")?.asText())
    }
}
