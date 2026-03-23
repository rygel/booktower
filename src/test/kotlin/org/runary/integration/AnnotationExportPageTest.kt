package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for annotation export buttons on the profile page.
 * Verifies the full stack: profile page HTML + export API endpoints.
 */
class AnnotationExportPageTest : IntegrationTestBase() {
    @Test
    fun `profile page has export section`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/profile").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("export") || html.contains("Export"), "Profile should have export section")
        assertTrue(html.contains("/api/export/annotations/markdown"), "Should have markdown export link")
        assertTrue(html.contains("/api/export/annotations/readwise"), "Should have readwise export link")
        assertTrue(html.contains("/api/export/annotations"), "Should have JSON export link")
    }

    @Test
    fun `markdown export returns markdown file`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/export/annotations/markdown").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("text/markdown") == true)
        assertTrue(resp.header("Content-Disposition")?.contains("attachment") == true)
        assertTrue(resp.bodyString().contains("Runary"), "Markdown should contain Runary header")
    }

    @Test
    fun `readwise CSV export returns CSV`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/export/annotations/readwise").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("text/csv") == true)
        assertTrue(resp.bodyString().startsWith("Highlight,Title,Author"), "CSV should have header row")
    }

    @Test
    fun `JSON export returns annotations and bookmarks`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/export/annotations").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val body = resp.bodyString()
        assertTrue(body.contains("annotations"), "Should have annotations key")
        assertTrue(body.contains("bookmarks"), "Should have bookmarks key")
    }
}
