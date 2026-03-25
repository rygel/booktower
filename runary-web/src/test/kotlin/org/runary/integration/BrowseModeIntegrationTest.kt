package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrowseModeIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET browse-mode returns default grid for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/user/preferences/browse-mode").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(
            "grid",
            Json.mapper
                .readTree(resp.bodyString())
                .get("browseMode")
                .asText(),
        )
    }

    @Test
    fun `PUT sets browse mode to list`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/user/preferences/browse-mode")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"browseMode":"list"}"""),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals(
            "list",
            Json.mapper
                .readTree(resp.bodyString())
                .get("browseMode")
                .asText(),
        )
    }

    @Test
    fun `GET returns updated mode after PUT`() {
        val token = registerAndGetToken()
        app(
            Request(Method.PUT, "/api/user/preferences/browse-mode")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"browseMode":"table"}"""),
        )
        val resp = app(Request(Method.GET, "/api/user/preferences/browse-mode").header("Cookie", "token=$token"))
        assertEquals(
            "table",
            Json.mapper
                .readTree(resp.bodyString())
                .get("browseMode")
                .asText(),
        )
    }

    @Test
    fun `PUT with invalid mode returns 400`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/user/preferences/browse-mode")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"browseMode":"tiles"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `PUT with missing browseMode field returns 400`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/user/preferences/browse-mode")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `each user has independent browse mode`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")

        app(
            Request(Method.PUT, "/api/user/preferences/browse-mode")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"browseMode":"list"}"""),
        )

        val resp2 = app(Request(Method.GET, "/api/user/preferences/browse-mode").header("Cookie", "token=$token2"))
        assertEquals(
            "grid",
            Json.mapper
                .readTree(resp2.bodyString())
                .get("browseMode")
                .asText(),
        )
    }

    @Test
    fun `browse-mode endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/user/preferences/browse-mode"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
