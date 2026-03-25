package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterPresetIntegrationTest : IntegrationTestBase() {
    private val sampleFilters = """{"status":"READ","tags":["sci-fi"],"ratingGte":4}"""

    @Test
    fun `GET presets returns empty list initially`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/user/filter-presets").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `POST creates a preset`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/user/filter-presets")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"My Sci-Fi","filters":$sampleFilters}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("My Sci-Fi", tree.get("name").asText())
        assertTrue(tree.get("filters").asText().contains("sci-fi"))
    }

    @Test
    fun `GET specific preset returns it`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/user/filter-presets")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Reading List","filters":$sampleFilters}"""),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val getResp = app(Request(Method.GET, "/api/user/filter-presets/$id").header("Cookie", "token=$token"))
        assertEquals(Status.OK, getResp.status)
        assertEquals(
            "Reading List",
            Json.mapper
                .readTree(getResp.bodyString())
                .get("name")
                .asText(),
        )
    }

    @Test
    fun `PUT updates a preset`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/user/filter-presets")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Old Name","filters":"{}"}"""),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val putResp =
            app(
                Request(Method.PUT, "/api/user/filter-presets/$id")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"New Name","filters":$sampleFilters}"""),
            )
        assertEquals(Status.OK, putResp.status)
        assertEquals(
            "New Name",
            Json.mapper
                .readTree(putResp.bodyString())
                .get("name")
                .asText(),
        )
    }

    @Test
    fun `DELETE removes preset`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/user/filter-presets")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"To Delete","filters":"{}"}"""),
            )
        val id =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val delResp = app(Request(Method.DELETE, "/api/user/filter-presets/$id").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val getResp = app(Request(Method.GET, "/api/user/filter-presets/$id").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, getResp.status)
    }

    @Test
    fun `POST with blank name returns 400`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/user/filter-presets")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"","filters":"{}"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `users only see their own presets`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")

        app(
            Request(Method.POST, "/api/user/filter-presets")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"name":"User1 Preset","filters":"{}"}"""),
        )

        val resp = app(Request(Method.GET, "/api/user/filter-presets").header("Cookie", "token=$token2"))
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `presets endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/user/filter-presets"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
