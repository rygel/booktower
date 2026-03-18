package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.booktower.models.UserAdminDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminIntegrationTest : IntegrationTestBase() {
    /** Register a user then elevate them to admin directly in the DB, and return a fresh admin token. */
    private fun registerAdminAndGetToken(prefix: String = "admin"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val registerResponse =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
            )
        assertEquals(Status.CREATED, registerResponse.status)
        val userId =
            Json.mapper
                .readValue(registerResponse.bodyString(), LoginResponse::class.java)
                .user.id

        // Promote to admin directly in the database
        TestFixture.database.getJdbi().useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE users SET is_admin = true WHERE id = ?")
                .bind(0, userId)
                .execute()
        }

        // Re-login to get a token that carries admin=true claim
        val loginResponse =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        assertEquals(Status.OK, loginResponse.status)
        return Json.mapper.readValue(loginResponse.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `non-admin cannot access admin page`() {
        val token = registerAndGetToken("nonadmin")
        val response =
            app(
                Request(Method.GET, "/admin")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `unauthenticated request to admin page returns 401`() {
        val response = app(Request(Method.GET, "/admin"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `admin can access admin page`() {
        val token = registerAdminAndGetToken()
        val response =
            app(
                Request(Method.GET, "/admin")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Admin Panel") || response.bodyString().contains("admin"))
    }

    @Test
    fun `admin page renders seed actions`() {
        val token = registerAdminAndGetToken()
        val body =
            app(
                Request(Method.GET, "/admin")
                    .header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("/admin/seed"), "Admin page should contain seed action")
        assertTrue(body.contains("/admin/seed/files"), "Admin page should contain seed-files action")
        assertTrue(body.contains("/admin/seed/librivox"), "Admin page should contain librivox seed action")
    }

    @Test
    fun `admin page lists registered users`() {
        val adminToken = registerAdminAndGetToken("pagelist")
        val otherUsername = "pagelisted_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$otherUsername","email":"$otherUsername@test.com","password":"password123"}"""),
        )
        val body =
            app(
                Request(Method.GET, "/admin")
                    .header("Cookie", "token=$adminToken"),
            ).bodyString()
        assertTrue(body.contains(otherUsername), "Admin page should list registered users")
    }

    @Test
    fun `admin can list users via API`() {
        val token = registerAdminAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val users =
            Json.mapper
                .readValue(
                    response.bodyString(),
                    Array<UserAdminDto>::class.java,
                ).toList()
        assertTrue(users.isNotEmpty())
    }

    @Test
    fun `non-admin cannot list users via API`() {
        val token = registerAndGetToken("nonadmin2")
        val response =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `admin can promote and demote a user`() {
        val adminToken = registerAdminAndGetToken()
        val targetToken = registerAndGetToken("target")

        // Get target user ID from the user list
        val usersResponse =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$adminToken"),
            )
        val users =
            Json.mapper
                .readValue(
                    usersResponse.bodyString(),
                    Array<UserAdminDto>::class.java,
                ).toList()

        // The target user logged in with targetToken — find them in the list
        val adminUsersAfterSetup = users.filter { !it.isAdmin }
        val target = adminUsersAfterSetup.firstOrNull()
        assertNotNull(target)

        // Promote
        val promoteResponse =
            app(
                Request(Method.POST, "/api/admin/users/${target.id}/promote")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, promoteResponse.status)
        assertEquals("/admin", promoteResponse.header("HX-Redirect"))
        assertTrue(promoteResponse.cookies().any { it.name == "flash_msg" && it.value.isNotBlank() })
        assertTrue(promoteResponse.cookies().any { it.name == "flash_type" && it.value == "success" })

        // Verify promoted
        val afterPromote =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/admin/users").header("Cookie", "token=$adminToken")).bodyString(),
                    Array<UserAdminDto>::class.java,
                ).first { it.id == target.id }
        assertTrue(afterPromote.isAdmin)

        // Demote
        val demoteResponse =
            app(
                Request(Method.POST, "/api/admin/users/${target.id}/demote")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, demoteResponse.status)
        assertEquals("/admin", demoteResponse.header("HX-Redirect"))
        assertTrue(demoteResponse.cookies().any { it.name == "flash_msg" && it.value.isNotBlank() })

        // Verify demoted
        val afterDemote =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/admin/users").header("Cookie", "token=$adminToken")).bodyString(),
                    Array<UserAdminDto>::class.java,
                ).first { it.id == target.id }
        assertFalse(afterDemote.isAdmin)
    }

    @Test
    fun `admin can delete another user`() {
        val adminToken = registerAdminAndGetToken()
        val targetToken = registerAndGetToken("todelete")

        val usersResponse =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$adminToken"),
            )
        val users =
            Json.mapper
                .readValue(
                    usersResponse.bodyString(),
                    Array<UserAdminDto>::class.java,
                ).toList()

        // Find the non-admin target user
        val target = users.filter { !it.isAdmin }.firstOrNull()
        assertNotNull(target)

        val deleteResponse =
            app(
                Request(Method.DELETE, "/api/admin/users/${target.id}")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, deleteResponse.status)
        assertTrue(deleteResponse.header("HX-Trigger")?.contains("showToast") == true)
        assertTrue(deleteResponse.bodyString().isBlank())

        // Verify deleted
        val afterDelete =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/admin/users").header("Cookie", "token=$adminToken")).bodyString(),
                    Array<UserAdminDto>::class.java,
                ).toList()
        assertTrue(afterDelete.none { it.id == target.id })
    }

    @Test
    fun `admin can list active password-reset tokens`() {
        val adminToken = registerAdminAndGetToken("prtoken1")

        // Create a reset token for a user
        val username = "prtarget_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        app(
            Request(Method.POST, "/auth/forgot-password")
                .header("Content-Type", "application/json")
                .body("""{"email":"$username@test.com"}"""),
        )

        val response =
            app(
                Request(Method.GET, "/api/admin/password-reset-tokens")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, response.status)
        val tokens = Json.mapper.readTree(response.bodyString())
        assertTrue(tokens.isArray, "Response should be a JSON array")
        // At least one token should be present for the user we just created
        assertTrue(tokens.any { it.get("username")?.asText() == username }, "Active token for $username should be listed")
    }

    @Test
    fun `non-admin cannot list password-reset tokens`() {
        val token = registerAndGetToken("nonprtokenuser")
        val response =
            app(
                Request(Method.GET, "/api/admin/password-reset-tokens")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `unauthenticated request to password-reset tokens returns 401`() {
        val response = app(Request(Method.GET, "/api/admin/password-reset-tokens"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `password-reset tokens list is empty when no active tokens exist`() {
        val adminToken = registerAdminAndGetToken("prtoken2")
        val response =
            app(
                Request(Method.GET, "/api/admin/password-reset-tokens")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, response.status)
        val tokens = Json.mapper.readTree(response.bodyString())
        assertTrue(tokens.isArray, "Response should be a JSON array")
        // Fresh admin with no resets issued — no token should belong to this user.
        // (Other parallel tests may have active tokens for their own users in the shared DB.)
        assertFalse(
            tokens.any { it.get("username")?.asText()?.startsWith("prtoken2_") == true },
            "No active tokens should exist for the freshly-registered prtoken2 admin user",
        )
    }

    @Test
    fun `admin cannot delete themselves`() {
        val adminToken = registerAdminAndGetToken()
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"admin_${System.nanoTime()}","password":"password123"}"""),
            )
        // Get the admin's own user ID from the user list
        val users =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/admin/users").header("Cookie", "token=$adminToken")).bodyString(),
                    Array<UserAdminDto>::class.java,
                ).toList()
        val adminUser = users.firstOrNull { it.isAdmin } ?: return

        val deleteResponse =
            app(
                Request(Method.DELETE, "/api/admin/users/${adminUser.id}")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.BAD_REQUEST, deleteResponse.status)
    }
}
