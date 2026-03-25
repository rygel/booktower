package org.runary.integration

import org.runary.config.Json
import org.runary.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmailProviderIntegrationTest : IntegrationTestBase() {
    private val validProvider = """{"name":"Test SMTP","host":"smtp.example.com","port":587,"username":"user@example.com","password":"secret","fromAddress":"noreply@example.com","useTls":true,"isDefault":false}"""

    private fun registerAdminAndGetToken(): String {
        val username = "admin_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val userId =
            Json.mapper
                .readValue(resp.bodyString(), LoginResponse::class.java)
                .user.id
        promoteToAdmin(userId)
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `GET email providers returns empty list initially`() {
        val adminToken = registerAdminAndGetToken()
        val resp = app(Request(Method.GET, "/api/admin/email-providers").header("Cookie", "token=$adminToken"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.isArray)
    }

    @Test
    fun `POST creates an email provider`() {
        val adminToken = registerAdminAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/admin/email-providers")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body(validProvider),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Test SMTP", tree.get("name").asText())
        assertEquals("smtp.example.com", tree.get("host").asText())
        assertEquals(587, tree.get("port").asInt())
        assertFalse(tree.get("isDefault").asBoolean())
    }

    @Test
    fun `GET returns created providers`() {
        val adminToken = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/api/admin/email-providers")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body(validProvider),
        )
        val resp = app(Request(Method.GET, "/api/admin/email-providers").header("Cookie", "token=$adminToken"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.size() >= 1)
    }

    @Test
    fun `PUT updates an email provider`() {
        val adminToken = registerAdminAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/email-providers")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body(validProvider),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val putResp =
            app(
                Request(Method.PUT, "/api/admin/email-providers/$id")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Updated SMTP","port":465}"""),
            )
        assertEquals(Status.OK, putResp.status)
        val tree = Json.mapper.readTree(putResp.bodyString())
        assertEquals("Updated SMTP", tree.get("name").asText())
        assertEquals(465, tree.get("port").asInt())
    }

    @Test
    fun `DELETE removes an email provider`() {
        val adminToken = registerAdminAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/email-providers")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body(validProvider),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val delResp =
            app(
                Request(Method.DELETE, "/api/admin/email-providers/$id").header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)
    }

    @Test
    fun `POST set-default marks provider as default`() {
        val adminToken = registerAdminAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/admin/email-providers")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body(validProvider),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val defaultResp =
            app(
                Request(Method.POST, "/api/admin/email-providers/$id/set-default")
                    .header("Cookie", "token=$adminToken"),
            )
        assertEquals(Status.NO_CONTENT, defaultResp.status)

        val listResp = app(Request(Method.GET, "/api/admin/email-providers").header("Cookie", "token=$adminToken"))
        val list = Json.mapper.readTree(listResp.bodyString())
        val provider = (0 until list.size()).map { list[it] }.firstOrNull { it.get("id").asText() == id }
        assertTrue(provider?.get("isDefault")?.asBoolean() == true)
    }

    @Test
    fun `creating two providers with isDefault=true leaves only one as default`() {
        val adminToken = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/api/admin/email-providers")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body(
                    """{"name":"First","host":"smtp1.example.com","port":587,"username":"u","password":"p","fromAddress":"a@b.com","useTls":true,"isDefault":true}""",
                ),
        )
        app(
            Request(Method.POST, "/api/admin/email-providers")
                .header("Cookie", "token=$adminToken")
                .header("Content-Type", "application/json")
                .body(
                    """{"name":"Second","host":"smtp2.example.com","port":587,"username":"u","password":"p","fromAddress":"a@b.com","useTls":true,"isDefault":true}""",
                ),
        )

        val listResp = app(Request(Method.GET, "/api/admin/email-providers").header("Cookie", "token=$adminToken"))
        val list = Json.mapper.readTree(listResp.bodyString())
        val defaults = (0 until list.size()).count { list[it].get("isDefault").asBoolean() }
        assertEquals(1, defaults, "Only one provider should be the default")
    }

    @Test
    fun `email provider endpoints require admin`() {
        val userToken = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/admin/email-providers").header("Cookie", "token=$userToken"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }

    @Test
    fun `POST with missing required field returns 400`() {
        val adminToken = registerAdminAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/admin/email-providers")
                    .header("Cookie", "token=$adminToken")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"No host","port":587,"username":"u","password":"p","fromAddress":"a@b.com"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }
}
