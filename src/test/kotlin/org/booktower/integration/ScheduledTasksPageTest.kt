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
 * End-to-end tests for the scheduled tasks admin page.
 */
class ScheduledTasksPageTest : IntegrationTestBase() {
    private fun registerAdmin(): String {
        val username = "admin_${System.nanoTime()}"
        val regResp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.booktower.TestPasswords.DEFAULT}"}"""),
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
                    .body("""{"username":"$username","password":"${org.booktower.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `scheduled tasks page requires admin`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/scheduled-tasks").header("Cookie", "token=$token"))
        assertTrue(resp.status == Status.FORBIDDEN || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `scheduled tasks page renders for admin`() {
        val token = registerAdmin()
        val resp = app(Request(Method.GET, "/scheduled-tasks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-task-btn"), "Should have add task button")
        assertTrue(html.contains("task-list"), "Should have task list container")
    }

    @Test
    fun `scheduled tasks page shows created task`() {
        val token = registerAdmin()
        // Create a scheduled task via API
        app(
            Request(Method.POST, "/api/admin/scheduled-tasks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Nightly Scan","taskType":"library.scan.all","cronExpression":"0 0 * * *"}"""),
        )

        val resp = app(Request(Method.GET, "/scheduled-tasks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Nightly Scan"), "Should show task name")
        assertTrue(html.contains("library.scan.all"), "Should show task type")
    }
}
