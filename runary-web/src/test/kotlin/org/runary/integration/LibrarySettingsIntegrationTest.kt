package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibrarySettingsIntegrationTest : IntegrationTestBase() {
    private fun createTestLibrary(token: String): String {
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Settings Test Lib","path":"./data/settings-test-${System.nanoTime()}"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    @Test
    fun `GET settings returns defaults for new library`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val resp = app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)

        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("formatAllowlist").isNull)
        assertTrue(tree.get("metadataSource").isNull)
        assertTrue(tree.get("defaultSort").isNull)
        assertEquals(0, tree.get("additionalPaths").size())
    }

    @Test
    fun `PUT settings updates format allowlist`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val putResp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"formatAllowlist":["pdf","epub"],"metadataSource":null,"defaultSort":null,"additionalPaths":null}"""),
            )
        assertEquals(Status.OK, putResp.status)

        val getResp = app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        val allowlist = tree.get("formatAllowlist")
        assertEquals(2, allowlist.size())
        val values = (0 until allowlist.size()).map { allowlist[it].asText() }.toSet()
        assertTrue("pdf" in values)
        assertTrue("epub" in values)
    }

    @Test
    fun `PUT settings updates metadata source`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val putResp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"formatAllowlist":null,"metadataSource":"googlebooks","defaultSort":null,"additionalPaths":null}"""),
            )
        assertEquals(Status.OK, putResp.status)

        val getResp = app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertEquals("googlebooks", tree.get("metadataSource").asText())
    }

    @Test
    fun `PUT settings updates default sort`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val putResp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"formatAllowlist":null,"metadataSource":null,"defaultSort":"author","additionalPaths":null}"""),
            )
        assertEquals(Status.OK, putResp.status)

        val getResp = app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertEquals("author", tree.get("defaultSort").asText())
    }

    @Test
    fun `PUT settings with additional paths stores them`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val putResp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(
                        """{"formatAllowlist":null,"metadataSource":null,"defaultSort":null,"additionalPaths":["./data/extra1","./data/extra2"]}""",
                    ),
            )
        assertEquals(Status.OK, putResp.status)

        val tree =
            Json.mapper.readTree(
                app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token")).bodyString(),
            )
        val paths = (0 until tree.get("additionalPaths").size()).map { tree.get("additionalPaths")[it].asText() }
        assertTrue("./data/extra1" in paths)
        assertTrue("./data/extra2" in paths)
    }

    @Test
    fun `PUT settings replaces additional paths on subsequent update`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        app(
            Request(Method.PUT, "/api/libraries/$libId/settings")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"additionalPaths":["./data/old"]}"""),
        )

        app(
            Request(Method.PUT, "/api/libraries/$libId/settings")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"additionalPaths":["./data/new"]}"""),
        )

        val tree =
            Json.mapper.readTree(
                app(Request(Method.GET, "/api/libraries/$libId/settings").header("Cookie", "token=$token")).bodyString(),
            )
        val paths = (0 until tree.get("additionalPaths").size()).map { tree.get("additionalPaths")[it].asText() }
        assertEquals(listOf("./data/new"), paths)
    }

    @Test
    fun `PUT settings with invalid metadata source returns 400`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val resp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"metadataSource":"nonexistent"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `PUT settings with invalid sort field returns 400`() {
        val token = registerAndGetToken()
        val libId = createTestLibrary(token)

        val resp =
            app(
                Request(Method.PUT, "/api/libraries/$libId/settings")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"defaultSort":"invalid_field"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `GET settings for non-existent library returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/api/libraries/00000000-0000-0000-0000-000000000000/settings")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `settings endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/libraries/00000000-0000-0000-0000-000000000000/settings"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
