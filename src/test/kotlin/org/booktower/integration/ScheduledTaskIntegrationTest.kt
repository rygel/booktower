package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTaskIntegrationTest : IntegrationTestBase() {
    private fun adminToken(): String {
        val username = "admin_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
            )
        val userId =
            Json.mapper
                .readValue(resp.bodyString(), LoginResponse::class.java)
                .user.id
        TestFixture.database.getJdbi().useHandle<Exception> { h ->
            h.createUpdate("UPDATE users SET is_admin = true WHERE id = ?").bind(0, userId).execute()
        }
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    private val validTask = """{"name":"Nightly Scan","taskType":"library.scan.all","cronExpression":"0 2 * * *","enabled":true}"""

    @Test
    fun `GET scheduled tasks returns empty list initially`() {
        val token = adminToken()
        val resp = app(Request(Method.GET, "/api/admin/scheduled-tasks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(Json.mapper.readTree(resp.bodyString()).isArray)
    }

    @Test
    fun `POST creates a scheduled task`() {
        val token = adminToken()
        val resp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(validTask),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Nightly Scan", tree.get("name").asText())
        assertEquals("library.scan.all", tree.get("taskType").asText())
        assertEquals("0 2 * * *", tree.get("cronExpression").asText())
        assertTrue(tree.get("enabled").asBoolean())
    }

    @Test
    fun `PUT updates a scheduled task`() {
        val token = adminToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(validTask),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val putResp =
            app(
                Request(Method.PUT, "/api/admin/scheduled-tasks/$id")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Daily Scan","enabled":false}"""),
            )
        assertEquals(Status.OK, putResp.status)
        val tree = Json.mapper.readTree(putResp.bodyString())
        assertEquals("Daily Scan", tree.get("name").asText())
        assertTrue(!tree.get("enabled").asBoolean())
    }

    @Test
    fun `DELETE removes a scheduled task`() {
        val token = adminToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(validTask),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val delResp = app(Request(Method.DELETE, "/api/admin/scheduled-tasks/$id").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)
    }

    @Test
    fun `POST trigger returns history id`() {
        val token = adminToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(validTask),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val triggerResp = app(Request(Method.POST, "/api/admin/scheduled-tasks/$id/trigger").header("Cookie", "token=$token"))
        assertEquals(Status.OK, triggerResp.status)
        assertTrue(Json.mapper.readTree(triggerResp.bodyString()).has("historyId"))
    }

    @Test
    fun `GET history returns task run records`() {
        val token = adminToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(validTask),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()
        app(Request(Method.POST, "/api/admin/scheduled-tasks/$id/trigger").header("Cookie", "token=$token"))

        val histResp = app(Request(Method.GET, "/api/admin/scheduled-tasks/$id/history").header("Cookie", "token=$token"))
        assertEquals(Status.OK, histResp.status)
        val history = Json.mapper.readTree(histResp.bodyString())
        assertTrue(history.isArray && history.size() >= 1)
        assertEquals("TRIGGERED", history[0].get("status").asText())
    }

    @Test
    fun `POST with invalid task type returns 400`() {
        val token = adminToken()
        val resp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Bad","taskType":"nonexistent","cronExpression":"* * * * *"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST with invalid cron expression returns 400`() {
        val token = adminToken()
        val resp =
            app(
                Request(Method.POST, "/api/admin/scheduled-tasks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Bad","taskType":"library.scan.all","cronExpression":"not-a-cron"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `scheduled tasks endpoints require admin`() {
        val userToken = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/admin/scheduled-tasks").header("Cookie", "token=$userToken"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
