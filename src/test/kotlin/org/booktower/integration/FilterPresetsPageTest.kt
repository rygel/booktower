package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the filter presets page.
 */
class FilterPresetsPageTest : IntegrationTestBase() {
    @Test
    fun `filter presets page requires authentication`() {
        val resp = app(Request(Method.GET, "/filter-presets"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `filter presets page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/filter-presets").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-preset-btn"), "Should have add preset button")
        assertTrue(html.contains("preset-list"), "Should have preset list container")
    }

    @Test
    fun `filter presets page shows created preset`() {
        val token = registerAndGetToken()
        // Create a filter preset via API
        app(
            Request(Method.POST, "/api/user/filter-presets")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Currently Reading","filters":{"status":"READING"}}"""),
        )

        val resp = app(Request(Method.GET, "/filter-presets").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Currently Reading"), "Should show preset name")
        assertTrue(html.contains("preset-delete"), "Should have delete button")
    }

    @Test
    fun `sidebar contains filter presets link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/filter-presets\""), "Sidebar should have /filter-presets link")
    }
}
