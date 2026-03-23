package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the device management page.
 */
class DeviceManagementPageTest : IntegrationTestBase() {
    @Test
    fun `devices page requires authentication`() {
        val resp = app(Request(Method.GET, "/devices"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `devices page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/devices").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-kobo-btn"), "Should have Kobo register button")
        assertTrue(html.contains("add-koreader-btn"), "Should have KOReader register button")
        assertTrue(html.contains("kobo-device-list"), "Should have Kobo device list")
        assertTrue(html.contains("koreader-device-list"), "Should have KOReader device list")
    }

    @Test
    fun `devices page shows registered Kobo device`() {
        val token = registerAndGetToken()
        // Register a Kobo device via API
        val regResp = app(
            Request(Method.POST, "/api/kobo/devices")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"deviceName":"My Test Kobo"}"""),
        )
        assertTrue(regResp.status == Status.OK || regResp.status == Status.CREATED)

        val resp = app(Request(Method.GET, "/devices").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("My Test Kobo"), "Should show Kobo device name")
        assertTrue(html.contains("kobo-delete"), "Should have Kobo delete button")
    }

    @Test
    fun `devices page shows registered KOReader device`() {
        val token = registerAndGetToken()
        // Register a KOReader device via API
        val regResp = app(
            Request(Method.POST, "/api/koreader/devices")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"deviceName":"My KOReader"}"""),
        )
        assertTrue(regResp.status == Status.OK || regResp.status == Status.CREATED)

        val resp = app(Request(Method.GET, "/devices").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("My KOReader"), "Should show KOReader device name")
        assertTrue(html.contains("koreader-delete"), "Should have KOReader delete button")
    }

    @Test
    fun `sidebar contains devices link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/devices\""), "Sidebar should have /devices link")
    }
}
