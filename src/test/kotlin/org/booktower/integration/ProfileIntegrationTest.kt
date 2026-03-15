package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileIntegrationTest : IntegrationTestBase() {

    @Test
    fun `profile page renders for authenticated user`() {
        val token = registerAndGetToken("prof1")
        val response = app(Request(Method.GET, "/profile").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
    }

    @Test
    fun `profile page redirects to login when not authenticated`() {
        val response = app(Request(Method.GET, "/profile"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertEquals("/login", response.header("Location"))
    }

    @Test
    fun `profile page shows account information section`() {
        val token = registerAndGetToken("prof2")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Account Information") || body.contains("account.info"),
            "Profile should show account information section")
    }

    @Test
    fun `profile page shows the user's username`() {
        val token = registerAndGetToken("prof3")
        // The username is "prof3_<timestamp>", so check for the prefix
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("prof3_"), "Profile should display the username")
    }

    @Test
    fun `profile page shows the user's email`() {
        val token = registerAndGetToken("prof4")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        // Email is "<username>@test.com" per IntegrationTestBase
        assertTrue(body.contains("@test.com"), "Profile should display the user's email")
    }

    @Test
    fun `profile page shows member since date`() {
        val token = registerAndGetToken("prof5")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        val currentYear = java.time.Year.now().value.toString()
        assertTrue(body.contains(currentYear), "Profile should show member since date containing current year")
    }

    @Test
    fun `profile page shows change password section`() {
        val token = registerAndGetToken("prof6")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("currentPassword") || body.contains("change-password") || body.contains("Change Password"),
            "Profile should still show change password section")
    }
}
