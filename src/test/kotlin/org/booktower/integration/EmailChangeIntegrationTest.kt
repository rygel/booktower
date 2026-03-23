package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailChangeIntegrationTest : IntegrationTestBase() {
    private fun changeEmail(
        token: String,
        newEmail: String,
        currentPassword: String,
    ): org.http4k.core.Response =
        app(
            Request(Method.POST, "/api/auth/change-email")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"$currentPassword","newEmail":"$newEmail"}"""),
        )

    @Test
    fun `change email returns 200 with valid credentials`() {
        val token = registerAndGetToken("em1")
        val response = changeEmail(token, "new_em1_${System.nanoTime()}@example.com", org.booktower.TestPasswords.DEFAULT)
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `change email fails with wrong password`() {
        val token = registerAndGetToken("em2")
        val response = changeEmail(token, "new_em2_${System.nanoTime()}@example.com", "wrongpassword")
        assertEquals(Status.BAD_REQUEST, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("password") || body.contains("incorrect"), "Error should mention password: $body")
    }

    @Test
    fun `change email fails without authentication`() {
        val response =
            app(
                Request(Method.POST, "/api/auth/change-email")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newEmail":"x@example.com"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `change email fails when email already in use`() {
        val token1 = registerAndGetToken("em3a")
        // Register a second user with a known email
        val knownEmail = "unique_em3_${System.nanoTime()}@example.com"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"em3c_${System.nanoTime()}","email":"$knownEmail","password":"${org.booktower.TestPasswords.DEFAULT}"}"""),
        )
        // Try to change token1's email to the already-used email
        val response = changeEmail(token1, knownEmail, org.booktower.TestPasswords.DEFAULT)
        assertEquals(Status.BAD_REQUEST, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("use") || body.contains("Email"), "Error should mention email is taken: $body")
    }

    @Test
    fun `change email fails with invalid email format`() {
        val token = registerAndGetToken("em4")
        val response = changeEmail(token, "not-an-email", org.booktower.TestPasswords.DEFAULT)
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `profile page shows change email form`() {
        val token = registerAndGetToken("em5")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            body.contains("change-email") || body.contains("new-email") || body.contains("email-form"),
            "Profile page should show change email form",
        )
    }

    @Test
    fun `email is actually updated after change`() {
        val token = registerAndGetToken("em6")
        val newEmail = "updated_em6_${System.nanoTime()}@example.com"
        val changeResp = changeEmail(token, newEmail, org.booktower.TestPasswords.DEFAULT)
        assertEquals(Status.OK, changeResp.status)
        // Check profile page shows new email
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains(newEmail.lowercase()), "Profile should show updated email, but got: $body")
    }

    @Test
    fun `change email with empty body returns 400`() {
        val token = registerAndGetToken("em7")
        val response =
            app(
                Request(Method.POST, "/api/auth/change-email")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(""),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `change email normalises email to lowercase`() {
        val token = registerAndGetToken("em8")
        val newEmail = "Mixed_Case_${System.nanoTime()}@Example.COM"
        val response = changeEmail(token, newEmail, org.booktower.TestPasswords.DEFAULT)
        assertEquals(Status.OK, response.status)
        // Profile should show lowercased version
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains(newEmail.lowercase()), "Profile should show lowercased email")
    }
}
