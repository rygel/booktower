package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PasswordChangeIntegrationTest : IntegrationTestBase() {
    @Test
    fun `happy path - correct current password changes successfully`() {
        val token = registerAndGetToken("pwchange")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":"newpassword456"}"""),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `wrong current password is rejected with 400`() {
        val token = registerAndGetToken("pwwrong")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"wrongpassword","newPassword":"newpassword456"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `new password too short is rejected with 400`() {
        val token = registerAndGetToken("pwshort")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":"abc"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `blank current password is rejected with 400`() {
        val token = registerAndGetToken("pwblank")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"","newPassword":"newpassword456"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `blank new password is rejected with 400`() {
        val token = registerAndGetToken("pwblanknew")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":""}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `empty body returns 400`() {
        val token = registerAndGetToken("pwempty")
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `unauthenticated request returns 401`() {
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":"newpassword456"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `new password works for login after change`() {
        val username = "pwlogin_${System.nanoTime()}"
        val registerResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.booktower.TestPasswords.DEFAULT}"}"""),
            )
        val token =
            org.booktower.config.Json.mapper
                .readValue(registerResp.bodyString(), org.booktower.models.LoginResponse::class.java)
                .token

        app(
            Request(Method.POST, "/api/auth/change-password")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":"brandnewpass"}"""),
        )

        // old password login fails
        val oldLogin =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"${org.booktower.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, oldLogin.status)

        // new password login succeeds
        val newLogin =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"brandnewpass"}"""),
            )
        assertEquals(Status.OK, newLogin.status)
    }

    @Test
    fun `rate limiter applies to change-password endpoint`() {
        val token = registerAndGetToken("pwrate")
        val ip = "10.77.${System.nanoTime() % 255}.1"
        repeat(10) {
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"currentPassword":"${org.booktower.TestPasswords.DEFAULT}","newPassword":"newpass_$it"}"""),
            )
        }
        val resp =
            app(
                Request(Method.POST, "/api/auth/change-password")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"currentPassword":"irrelevant","newPassword":"aaabbbccc"}"""),
            )
        assertEquals(Status.TOO_MANY_REQUESTS, resp.status)
    }
}
