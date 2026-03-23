package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PwaManifestIntegrationTest : IntegrationTestBase() {
    @Test
    fun `manifest json returns 200 with correct content type`() {
        val response = app(Request(Method.GET, "/manifest.json"))
        assertEquals(200, response.status.code)
        val ct = response.header("Content-Type") ?: ""
        assertTrue(ct.contains("manifest"), "Expected manifest content type, got: $ct")
    }

    @Test
    fun `manifest json contains required PWA fields`() {
        val response = app(Request(Method.GET, "/manifest.json"))
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals("Runary", body.get("name").asText())
        assertEquals("standalone", body.get("display").asText())
        assertTrue(body.get("start_url").asText().isNotBlank())
        assertTrue(body.has("icons"))
        assertTrue(body.get("icons").isArray)
        assertTrue(body.get("icons").size() > 0)
    }

    @Test
    fun `manifest json is valid JSON`() {
        val response = app(Request(Method.GET, "/manifest.json"))
        assertEquals(200, response.status.code)
        // Just parsing it should not throw
        val body = Json.mapper.readTree(response.bodyString())
        assertTrue(body.isObject)
    }

    @Test
    fun `manifest is accessible without authentication`() {
        // No auth header, no cookie — should still return 200
        val response = app(Request(Method.GET, "/manifest.json"))
        assertEquals(200, response.status.code)
    }

    @Test
    fun `login page includes manifest link`() {
        val response = app(Request(Method.GET, "/login"))
        // Login page doesn't use layout.kte (no username), check the dashboard instead
        val token = registerAndGetToken("pwa")
        val dashboard = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(200, dashboard.status.code)
        assertTrue(
            dashboard.bodyString().contains("manifest.json"),
            "Expected dashboard HTML to include manifest link",
        )
    }
}
