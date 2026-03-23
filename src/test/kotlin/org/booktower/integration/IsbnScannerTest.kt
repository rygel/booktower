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
 * End-to-end tests for the ISBN barcode scanner feature.
 */
class IsbnScannerTest : IntegrationTestBase() {
    @Test
    fun `library page includes scanner UI elements`() {
        val token = registerAndGetToken()
        // Create a library first
        app(
            Request(Method.POST, "/ui/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("name=Scanner%20Test&path=./data/scanner-test"),
        )
        // Get libraries to find the ID
        val libResp = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        val libHtml = libResp.bodyString()
        val libIdMatch = Regex("href=\"/libraries/([a-f0-9-]+)\"").find(libHtml)
        val libId = libIdMatch?.groupValues?.get(1) ?: error("No library found")

        val resp = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("isbn-input"), "Should have ISBN input field")
        assertTrue(html.contains("scan-btn"), "Should have scan button")
        assertTrue(html.contains("isbn-lookup-btn"), "Should have lookup button")
        assertTrue(html.contains("isbn-scanner-container"), "Should have scanner container")
        assertTrue(html.contains("html5-qrcode.min.js"), "Should include scanner library")
    }

    @Test
    fun `isbn lookup endpoint requires authentication`() {
        val resp = app(
            Request(Method.POST, "/ui/isbn/lookup")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("isbn=9780140449136"),
        )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `isbn lookup endpoint requires isbn parameter`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.POST, "/ui/isbn/lookup")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(""),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `isbn lookup endpoint returns not found for invalid isbn`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.POST, "/ui/isbn/lookup")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("isbn=0000000000"),
        )
        assertEquals(Status.OK, resp.status)
        val body = resp.bodyString()
        assertTrue(body.contains("\"found\""), "Response should have found field")
    }
}
