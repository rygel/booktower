package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse

class LibraryAccessIntegrationTest : IntegrationTestBase() {
    private fun registerAdminAndGetToken(): Pair<String, String> {
        val u = "lacadmin_${System.nanoTime()}"
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
        promoteToAdmin(userId)
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","password":"pass1234"}"""),
            )
        return userId to Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    private fun registerUserAndGetTokenAndId(): Triple<String, String, String> {
        val u = "lacuser_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        val lr = Json.mapper.readValue(r.bodyString(), LoginResponse::class.java)
        return Triple(lr.user.id, lr.token, u)
    }

    @Test
    fun `GET library-access returns not restricted by default`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val (userId, _, _) = registerUserAndGetTokenAndId()

        val resp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/library-access")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("restricted")?.asBoolean() ?: true)
        assertTrue(tree.get("grants")?.isArray == true)
    }

    @Test
    fun `PUT library-access sets restricted flag`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val (userId, _, _) = registerUserAndGetTokenAndId()

        val resp =
            app(
                Request(Method.PUT, "/api/admin/users/$userId/library-access")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body("""{"restricted":true}"""),
            )
        assertEquals(Status.NO_CONTENT, resp.status)

        val check =
            app(
                Request(Method.GET, "/api/admin/users/$userId/library-access")
                    .header("Cookie", "token=$adminToken"),
            )
        val tree = Json.mapper.readTree(check.bodyString())
        assertTrue(tree.get("restricted")?.asBoolean() == true)
    }

    @Test
    fun `POST library-access grants a library to a restricted user`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val (userId, userToken, _) = registerUserAndGetTokenAndId()
        val libId = createLibrary(userToken)

        // Restrict the user
        app(
            Request(Method.PUT, "/api/admin/users/$userId/library-access")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body("""{"restricted":true}"""),
        )

        // Grant access to the library
        val grantResp =
            app(
                Request(Method.POST, "/api/admin/users/$userId/library-access")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body("""{"libraryId":"$libId"}"""),
            )
        assertEquals(Status.NO_CONTENT, grantResp.status)

        // Library should now be visible
        val libResp = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$userToken"))
        val arr = Json.mapper.readTree(libResp.bodyString())
        assertTrue(arr.any { it.get("id")?.asText() == libId })
    }

    @Test
    fun `restricted user cannot see libraries not granted`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val (userId, userToken, _) = registerUserAndGetTokenAndId()
        createLibrary(userToken) // create a library for the user

        // Restrict — no grants
        app(
            Request(Method.PUT, "/api/admin/users/$userId/library-access")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body("""{"restricted":true}"""),
        )

        val libResp = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$userToken"))
        val arr = Json.mapper.readTree(libResp.bodyString())
        assertTrue(arr.size() == 0)
    }

    @Test
    fun `DELETE library-access revokes a grant`() {
        val (_, adminToken) = registerAdminAndGetToken()
        val (userId, userToken, _) = registerUserAndGetTokenAndId()
        val libId = createLibrary(userToken)

        // Restrict and grant
        app(
            Request(Method.PUT, "/api/admin/users/$userId/library-access")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body("""{"restricted":true}"""),
        )
        app(
            Request(Method.POST, "/api/admin/users/$userId/library-access")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body("""{"libraryId":"$libId"}"""),
        )

        // Revoke
        val del =
            app(
                Request(Method.DELETE, "/api/admin/users/$userId/library-access/$libId")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.NO_CONTENT, del.status)

        // Should no longer be visible
        val libResp = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$userToken"))
        val arr = Json.mapper.readTree(libResp.bodyString())
        assertTrue(arr.none { it.get("id")?.asText() == libId })
    }

    @Test
    fun `non-restricted user sees all their libraries`() {
        val (_, userToken, _) = registerUserAndGetTokenAndId()
        val lib1 = createLibrary(userToken)
        val lib2 = createLibrary(userToken)

        val libResp = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$userToken"))
        val arr = Json.mapper.readTree(libResp.bodyString())
        val ids = arr.map { it.get("id")?.asText() }.toSet()
        assertTrue(lib1 in ids)
        assertTrue(lib2 in ids)
    }

    @Test
    fun `non-admin cannot access library-access endpoints`() {
        val (userId, _, _) = registerUserAndGetTokenAndId()
        val (_, regularToken, _) = registerUserAndGetTokenAndId()

        val resp =
            app(
                Request(Method.GET, "/api/admin/users/$userId/library-access")
                    .header("Cookie", "token=$regularToken"),
            )
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
