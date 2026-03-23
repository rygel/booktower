package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the content restrictions page.
 */
class ContentRestrictionsPageTest : IntegrationTestBase() {
    @Test
    fun `content restrictions page requires authentication`() {
        val resp = app(Request(Method.GET, "/content-restrictions"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `content restrictions page renders default state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/content-restrictions").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("max-age-rating"), "Should have age rating select")
        assertTrue(html.contains("blocked-tags"), "Should have blocked tags input")
        assertTrue(html.contains("restrictions-save-btn"), "Should have save button")
    }

    @Test
    fun `content restrictions page shows saved restrictions`() {
        val token = registerAndGetToken()
        // Set restrictions via API
        app(
            Request(Method.PUT, "/api/user/content-restrictions")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"maxAgeRating":"PG-13","blockedTags":["horror","explicit"]}"""),
        )

        val resp = app(Request(Method.GET, "/content-restrictions").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        // PG-13 should be selected in the dropdown
        assertTrue(html.contains("PG-13"), "Should show PG-13 rating")
        assertTrue(html.contains("horror"), "Should show blocked tags")
    }

    @Test
    fun `sidebar contains content restrictions link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/content-restrictions\""), "Sidebar should have /content-restrictions link")
    }
}
