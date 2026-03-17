package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentRestrictionsIntegrationTest : IntegrationTestBase() {

    @Test
    fun `GET content-restrictions returns unrestricted by default`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/user/content-restrictions").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("maxAgeRating").isNull || tree.get("maxAgeRating").asText().isBlank())
        assertTrue(tree.get("blockedTags").isArray && tree.get("blockedTags").size() == 0)
    }

    @Test
    fun `PUT content-restrictions sets max age rating`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"maxAgeRating":"PG-13"}"""),
        )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("PG-13", tree.get("maxAgeRating").asText())
    }

    @Test
    fun `PUT content-restrictions with invalid age rating returns 400`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"maxAgeRating":"INVALID"}"""),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `PUT content-restrictions sets blocked tags`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"blockedTags":["horror","gore"]}"""),
        )
        assertEquals(Status.OK, resp.status)
        val tags = Json.mapper.readTree(resp.bodyString()).get("blockedTags")
        assertEquals(2, tags.size())
        val names = (0 until tags.size()).map { tags[it].asText() }.toSet()
        assertTrue("horror" in names)
        assertTrue("gore" in names)
    }

    @Test
    fun `PUT content-restrictions clears max rating when set to null`() {
        val token = registerAndGetToken()
        app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"maxAgeRating":"R"}"""),
        )
        app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"maxAgeRating":null}"""),
        )
        val getResp = app(Request(Method.GET, "/api/user/content-restrictions").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertTrue(tree.get("maxAgeRating").isNull || tree.get("maxAgeRating").asText().isBlank())
    }

    @Test
    fun `content-restrictions endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/user/content-restrictions"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
