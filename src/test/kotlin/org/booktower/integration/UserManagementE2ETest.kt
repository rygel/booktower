package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.BookDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.models.UserAdminDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserManagementE2ETest : IntegrationTestBase() {
    private fun uniqueUser(prefix: String = "e2e") = "${prefix}_${System.nanoTime()}"

    private fun registerJson(
        username: String,
        email: String = "$username@test.com",
        password: String = "password123",
    ): String = """{"username":"$username","email":"$email","password":"$password"}"""

    private fun loginJson(
        username: String,
        password: String = "password123",
    ): String = """{"username":"$username","password":"$password"}"""

    private fun register(
        username: String,
        email: String = "$username@test.com",
        password: String = "password123",
    ): LoginResponse {
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(username, email, password)),
            )
        assertEquals(Status.CREATED, resp.status, "Registration of $username should succeed")
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java)
    }

    private fun login(
        username: String,
        password: String = "password123",
    ): LoginResponse {
        val resp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username, password)),
            )
        assertEquals(Status.OK, resp.status, "Login of $username should succeed")
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java)
    }

    /** Register a user, promote them to admin directly in the DB, and return a fresh admin token. */
    private fun registerAdminAndGetToken(prefix: String = "adm"): String {
        val username = uniqueUser(prefix)
        val registerResponse = register(username)
        val userId = registerResponse.user.id

        TestFixture.database.getJdbi().useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE users SET is_admin = true WHERE id = ?")
                .bind(0, userId)
                .execute()
        }

        // Re-login to get a token that carries admin=true claim
        return login(username).token
    }

    private fun listUsersAsAdmin(adminToken: String): List<UserAdminDto> {
        val resp =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, resp.status)
        return Json.mapper
            .readValue(resp.bodyString(), Array<UserAdminDto>::class.java)
            .toList()
    }

    // ── 1. Bulk user creation ────────────────────────────────────────────

    @Test
    fun `can register 20 users and all can login`() {
        val users =
            (1..20).map { i ->
                val username = "bulk_${System.nanoTime()}_$i"
                val loginResponse = register(username)
                Triple(username, "$username@test.com", loginResponse.token)
            }
        assertEquals(20, users.size)

        // Verify all can login
        users.forEach { (username, _, _) ->
            val loginResp =
                app(
                    Request(Method.POST, "/auth/login")
                        .header("Content-Type", "application/json")
                        .body(loginJson(username)),
                )
            assertEquals(Status.OK, loginResp.status, "User $username should be able to login")
        }
    }

    // ── 2. Admin can list all users ──────────────────────────────────────

    @Test
    fun `admin can list all registered users`() {
        val adminToken = registerAdminAndGetToken("listadm")

        // Create 10 regular users
        val regularUsers =
            (1..10).map { i ->
                val username = "listreg_${System.nanoTime()}_$i"
                register(username)
                username
            }

        val allUsers = listUsersAsAdmin(adminToken)
        // At least admin + 10 regular users
        assertTrue(allUsers.size >= 11, "Expected at least 11 users, got ${allUsers.size}")

        // Verify all regular users appear in the list
        regularUsers.forEach { username ->
            assertTrue(
                allUsers.any { it.username == username },
                "User $username should appear in admin user list",
            )
        }
    }

    // ── 3. Admin promote and demote ──────────────────────────────────────

    @Test
    fun `admin can promote and demote users`() {
        val adminToken = registerAdminAndGetToken("promadm")
        val targetUsername = uniqueUser("promtarget")
        val targetResponse = register(targetUsername)
        val targetUserId = targetResponse.user.id

        // Verify target is NOT admin
        val usersBefore = listUsersAsAdmin(adminToken)
        val targetBefore = usersBefore.first { it.id == targetUserId }
        assertFalse(targetBefore.isAdmin, "Target should not be admin initially")

        // Promote
        val promoteResp =
            app(
                Request(Method.POST, "/api/admin/users/$targetUserId/promote")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, promoteResp.status)

        // Verify promoted
        val usersAfterPromote = listUsersAsAdmin(adminToken)
        val targetAfterPromote = usersAfterPromote.first { it.id == targetUserId }
        assertTrue(targetAfterPromote.isAdmin, "Target should be admin after promotion")

        // Re-login as promoted user and verify they can access admin endpoints
        val promotedToken = login(targetUsername).token
        val adminAccess =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$promotedToken"),
            )
        assertEquals(Status.OK, adminAccess.status, "Promoted user should access admin endpoints")

        // Demote
        val demoteResp =
            app(
                Request(Method.POST, "/api/admin/users/$targetUserId/demote")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, demoteResp.status)

        // Verify demoted
        val usersAfterDemote = listUsersAsAdmin(adminToken)
        val targetAfterDemote = usersAfterDemote.first { it.id == targetUserId }
        assertFalse(targetAfterDemote.isAdmin, "Target should not be admin after demotion")

        // Re-login as demoted user and verify they can NO LONGER access admin endpoints
        val demotedToken = login(targetUsername).token
        val noAdminAccess =
            app(
                Request(Method.GET, "/api/admin/users")
                    .header("Cookie", "token=$demotedToken"),
            )
        assertEquals(Status.FORBIDDEN, noAdminAccess.status, "Demoted user should not access admin endpoints")
    }

    // ── 4. Admin delete user ─────────────────────────────────────────────

    @Test
    fun `admin can delete a user and they cannot login`() {
        val adminToken = registerAdminAndGetToken("deladm")
        val targetUsername = uniqueUser("deltarget")
        val targetResponse = register(targetUsername)
        val targetUserId = targetResponse.user.id
        val targetToken = targetResponse.token

        // Verify target can access protected endpoint
        val beforeDelete =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$targetToken"),
            )
        assertEquals(Status.OK, beforeDelete.status, "Target should access API before deletion")

        // Delete via admin API
        val deleteResp =
            app(
                Request(Method.DELETE, "/api/admin/users/$targetUserId")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, deleteResp.status)

        // Verify target no longer appears in user list
        val usersAfter = listUsersAsAdmin(adminToken)
        assertTrue(usersAfter.none { it.id == targetUserId }, "Deleted user should not appear in user list")

        // Verify target cannot login
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(targetUsername)),
            )
        assertEquals(Status.UNAUTHORIZED, loginResp.status, "Deleted user should not be able to login")

        // Verify target's old token is rejected
        val afterDelete =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$targetToken"),
            )
        assertEquals(Status.UNAUTHORIZED, afterDelete.status, "Deleted user's token should be rejected")
    }

    // ── 5. Password change ───────────────────────────────────────────────

    @Test
    fun `user can change password and login with new password`() {
        val username = uniqueUser("pwchg")
        register(username)
        val token = login(username).token

        // Change password
        val changeResp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"password123","newPassword":"newpassword456"}"""),
            )
        assertEquals(Status.OK, changeResp.status, "Password change should succeed")

        // Old password should fail
        val oldLoginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username, "password123")),
            )
        assertEquals(Status.UNAUTHORIZED, oldLoginResp.status, "Old password should no longer work")

        // New password should work
        val newLoginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body(loginJson(username, "newpassword456")),
            )
        assertEquals(Status.OK, newLoginResp.status, "New password should work")
    }

    // ── 6. Email change ──────────────────────────────────────────────────

    @Test
    fun `user can change email`() {
        val username = uniqueUser("emchg")
        register(username)
        val token = login(username).token
        val newEmail = "newemail_${System.nanoTime()}@example.com"

        // Change email
        val changeResp =
            app(
                Request(Method.POST, "/api/auth/change-email")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"password123","newEmail":"$newEmail"}"""),
            )
        assertEquals(Status.OK, changeResp.status, "Email change should succeed")

        // Verify new email is reflected in profile
        val profileResp =
            app(
                Request(Method.GET, "/profile")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, profileResp.status)
        assertTrue(
            profileResp.bodyString().contains(newEmail.lowercase()),
            "Profile should show updated email",
        )
    }

    // ── 7. User isolation — 50 users with their own data ─────────────────

    @Test
    fun `50 users each have isolated libraries and books`() {
        data class UserData(
            val username: String,
            val token: String,
            val libraryId: String,
            val bookId: String,
            val bookTitle: String,
        )

        val usersData =
            (1..50).map { i ->
                val username = "iso_${System.nanoTime()}_$i"
                val token = register(username).token
                val libraryId = createLibrary(token, "IsoLib_$i")
                val bookTitle = "IsoBook_${username}_$i"
                val bookId = createBook(token, libraryId, bookTitle)
                UserData(username, token, libraryId, bookId, bookTitle)
            }

        assertEquals(50, usersData.size, "Should have created 50 users")

        // Verify each user only sees their own library
        usersData.forEach { userData ->
            val libResp =
                app(
                    Request(Method.GET, "/api/libraries")
                        .header("Cookie", "token=${userData.token}"),
                )
            assertEquals(Status.OK, libResp.status)
            val libraries =
                Json.mapper
                    .readValue(libResp.bodyString(), Array<LibraryDto>::class.java)
                    .toList()
            assertEquals(1, libraries.size, "User ${userData.username} should see exactly 1 library")
            assertEquals(userData.libraryId, libraries[0].id, "User ${userData.username} should see their own library")
        }

        // Verify user A cannot see user B's books — pick first and second user
        val userA = usersData[0]
        val userB = usersData[1]

        val userABooks =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=${userA.token}"),
            )
        assertEquals(Status.OK, userABooks.status)
        val aBooksBody = userABooks.bodyString()
        assertFalse(
            aBooksBody.contains(userB.bookTitle),
            "User A should not see user B's book title in their book list",
        )

        val userBBooks =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=${userB.token}"),
            )
        assertEquals(Status.OK, userBBooks.status)
        val bBooksBody = userBBooks.bodyString()
        assertFalse(
            bBooksBody.contains(userA.bookTitle),
            "User B should not see user A's book title in their book list",
        )
    }

    // ── 8. Concurrent registration ───────────────────────────────────────

    @Test
    fun `concurrent registrations do not cause conflicts`() {
        val results = ConcurrentLinkedQueue<Pair<String, Int>>()
        val latch = CountDownLatch(20)
        val threads =
            (1..20).map { i ->
                Thread.ofVirtual().start {
                    try {
                        val username = "conc_${System.nanoTime()}_$i"
                        val resp =
                            app(
                                Request(Method.POST, "/auth/register")
                                    .header("Content-Type", "application/json")
                                    .body(registerJson(username)),
                            )
                        results.add(username to resp.status.code)
                    } finally {
                        latch.countDown()
                    }
                }
            }

        latch.await()

        val successes = results.filter { it.second == 201 }
        assertEquals(20, successes.size, "All 20 concurrent registrations should succeed, but got: $results")

        // Verify all unique usernames
        val usernames = successes.map { it.first }.toSet()
        assertEquals(20, usernames.size, "All usernames should be unique")
    }

    // ── 9. Duplicate username/email rejection ────────────────────────────

    @Test
    fun `duplicate username and email are rejected`() {
        val username = uniqueUser("dup")
        val email = "$username@test.com"
        register(username, email)

        // Duplicate username, different email
        val dupUsernameResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson(username, "other_${System.nanoTime()}@test.com")),
            )
        assertTrue(
            dupUsernameResp.status.code >= 400,
            "Duplicate username should be rejected, got ${dupUsernameResp.status}",
        )

        // Duplicate email, different username
        val dupEmailResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(registerJson("other_${System.nanoTime()}", email)),
            )
        assertTrue(
            dupEmailResp.status.code >= 400,
            "Duplicate email should be rejected, got ${dupEmailResp.status}",
        )
    }

    // ── 10. Password reset flow ──────────────────────────────────────────

    @Test
    fun `admin can generate password reset link`() {
        val adminToken = registerAdminAndGetToken("rstadm")
        val targetUsername = uniqueUser("rsttarget")
        val targetResponse = register(targetUsername)
        val targetUserId = targetResponse.user.id

        // Admin generates reset link
        val resetResp =
            app(
                Request(Method.POST, "/api/admin/users/$targetUserId/reset-password")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, resetResp.status, "Reset link generation should succeed")
        val resetBody = Json.mapper.readTree(resetResp.bodyString())
        assertNotNull(resetBody.get("resetLink"), "Response should contain a resetLink")
        assertTrue(
            resetBody.get("resetLink").asText().contains("reset-password?token="),
            "Reset link should contain a token parameter",
        )
    }

    // ── 11. Session management ───────────────────────────────────────────

    @Test
    fun `logout clears session cookie`() {
        val username = uniqueUser("sess")
        register(username)
        val token = login(username).token

        // Access protected endpoint — should succeed
        val beforeLogout =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, beforeLogout.status, "Should access API before logout")

        // Logout
        val logoutResp =
            app(
                Request(Method.POST, "/auth/logout")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(
            logoutResp.status == Status.OK || logoutResp.status == Status.SEE_OTHER,
            "Logout should succeed",
        )
        val cookie = logoutResp.cookies().find { it.name == "token" }
        assertNotNull(cookie, "Logout should set token cookie")
        assertEquals("", cookie.value, "Logout should clear token cookie value")
        assertEquals(0L, cookie.maxAge, "Logout should expire token cookie")
    }

    // ── 12. Rate limiting on auth endpoints ──────────────────────────────

    @Test
    fun `auth endpoints are rate limited`() {
        val ip = "10.99.${System.nanoTime() % 255}.1"
        val username = uniqueUser("rate")
        register(username)

        // Send 10 rapid login attempts (the rate limit window is 10 per 60s)
        repeat(10) {
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body(loginJson(username)),
            )
        }

        // The 11th request should be rate limited
        val rateLimitedResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body(loginJson(username)),
            )
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            rateLimitedResp.status,
            "11th login attempt from same IP should be rate limited",
        )
    }

    // ── 50 concurrent users: register, create data, verify isolation ────────

    @Test
    fun `50 concurrent users register, create libraries and books, all isolated`() {
        // Rebuild app to reset rate limiter state (previous tests may have exhausted it)
        app = buildApp()
        val userCount = 50
        val errors = ConcurrentLinkedQueue<String>()
        val tokens = ConcurrentLinkedQueue<Pair<String, String>>() // username to token
        val latch = CountDownLatch(userCount)

        // Phase 1: Register 50 users concurrently (each with unique IP to avoid rate limiting)
        val threads =
            (1..userCount).map { i ->
                Thread.startVirtualThread {
                    try {
                        val username = "conc50_${System.nanoTime()}_$i"
                        val resp =
                            app(
                                Request(Method.POST, "/auth/register")
                                    .header("Content-Type", "application/json")
                                    .header("X-Forwarded-For", "10.0.${i / 256}.${i % 256}")
                                    .body("""{"username":"$username","email":"$username@test.com","password":"password_$i"}"""),
                            )
                        if (resp.status.code != 201) {
                            errors.add("User $i registration failed: ${resp.status} body=${resp.bodyString().take(200)}")
                        } else {
                            val token = Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
                            tokens.add(username to token)
                        }
                    } catch (e: Exception) {
                        errors.add("User $i exception: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }
        latch.await()
        assertTrue(errors.isEmpty(), "All 50 registrations should succeed, but got errors: $errors")
        assertEquals(userCount, tokens.size, "Should have 50 tokens")

        // Phase 2: Each user creates a library and a book concurrently
        val latch2 = CountDownLatch(tokens.size)
        val userBooks = ConcurrentLinkedQueue<Triple<String, String, String>>() // username, libId, bookId
        tokens.forEach { (username, token) ->
            Thread.startVirtualThread {
                try {
                    val libResp =
                        app(
                            Request(Method.POST, "/api/libraries")
                                .header("Cookie", "token=$token")
                                .header("Content-Type", "application/json")
                                .body("""{"name":"Lib of $username","path":"./data/conc50-$username"}"""),
                        )
                    if (libResp.status.code != 201) {
                        errors.add("$username library creation failed: ${libResp.status}")
                    } else {
                        val libId = Json.mapper.readValue(libResp.bodyString(), LibraryDto::class.java).id
                        val bookResp =
                            app(
                                Request(Method.POST, "/api/books")
                                    .header("Cookie", "token=$token")
                                    .header("Content-Type", "application/json")
                                    .body("""{"title":"Book by $username","author":"$username","description":null,"libraryId":"$libId"}"""),
                            )
                        if (bookResp.status.code != 201) {
                            errors.add("$username book creation failed: ${bookResp.status}")
                        } else {
                            val bookId = Json.mapper.readValue(bookResp.bodyString(), BookDto::class.java).id
                            userBooks.add(Triple(username, libId, bookId))
                        }
                    }
                } catch (e: Exception) {
                    errors.add("$username data creation exception: ${e.message}")
                } finally {
                    latch2.countDown()
                }
            }
        }
        latch2.await()
        assertTrue(errors.isEmpty(), "All 50 users should create data, but got errors: $errors")
        assertEquals(userCount, userBooks.size, "Should have 50 user-book entries")

        // Phase 3: Verify isolation — each user sees only their own library
        val tokenMap = tokens.associate { it.first to it.second }
        val latch3 = CountDownLatch(userBooks.size)
        userBooks.forEach { (username, _, _) ->
            Thread.startVirtualThread {
                try {
                    val token = tokenMap[username]!!
                    val libsResp =
                        app(
                            Request(Method.GET, "/api/libraries")
                                .header("Cookie", "token=$token"),
                        )
                    val libs = Json.mapper.readValue(libsResp.bodyString(), Array<LibraryDto>::class.java)
                    if (libs.size != 1) {
                        errors.add("$username sees ${libs.size} libraries (expected 1)")
                    } else if (!libs[0].name.contains(username)) {
                        errors.add("$username sees wrong library: ${libs[0].name}")
                    }
                } catch (e: Exception) {
                    errors.add("$username isolation check exception: ${e.message}")
                } finally {
                    latch3.countDown()
                }
            }
        }
        latch3.await()
        assertTrue(errors.isEmpty(), "All 50 users should see only their own library: $errors")
    }
}
