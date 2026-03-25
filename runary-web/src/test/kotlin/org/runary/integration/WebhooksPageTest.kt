package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

/**
 * End-to-end test for the Webhooks management page.
 * Verifies full stack: PageHandler → JTE template → rendered HTML.
 */
class WebhooksPageTest : IntegrationTestBase() {
    @Test
    fun `webhooks page requires authentication`() {
        val resp = app(Request(Method.GET, "/webhooks"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `webhooks page renders empty state for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/webhooks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Webhooks") || html.contains("webhook"), "Page should contain webhooks title")
        assertTrue(html.contains("add-webhook-btn"), "Page should contain add button")
    }

    @Test
    fun `webhooks page shows created webhook`() {
        val token = registerAndGetToken()
        // Create a webhook via API
        app(
            Request(Method.POST, "/api/webhooks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Test Hook","url":"https://example.com/hook","events":["book.added"]}"""),
        )

        val resp = app(Request(Method.GET, "/webhooks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Test Hook"), "Page should show webhook name")
        assertTrue(html.contains("https://example.com/hook"), "Page should show webhook URL")
        assertTrue(html.contains("book.added"), "Page should show webhook event")
    }

    @Test
    fun `webhooks page has toggle and delete buttons`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/webhooks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Toggle Hook","url":"https://example.com","events":["book.finished"]}"""),
            )
        val hookId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/webhooks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("wh-toggle"), "Page should contain toggle button")
        assertTrue(html.contains("wh-delete"), "Page should contain delete button")
        assertTrue(html.contains(hookId), "Page should contain webhook ID in data attributes")
    }

    @Test
    fun `webhooks page has add form with event checkboxes`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/webhooks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-webhook-form"), "Page should contain add form")
        assertTrue(html.contains("book.added"), "Form should have book.added event")
        assertTrue(html.contains("book.deleted"), "Form should have book.deleted event")
        assertTrue(html.contains("book.finished"), "Form should have book.finished event")
        assertTrue(html.contains("download.complete"), "Form should have download.complete event")
        assertTrue(html.contains("library.scanned"), "Form should have library.scanned event")
    }

    @Test
    fun `sidebar contains webhooks link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/webhooks\""), "Sidebar should have /webhooks link")
    }
}
