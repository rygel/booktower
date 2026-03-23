package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataProposalsPageTest : IntegrationTestBase() {
    @Test
    fun `metadata proposals page requires authentication`() {
        val resp = app(Request(Method.GET, "/metadata-proposals"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `metadata proposals page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/metadata-proposals").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("proposal-list"), "Should have proposal list container")
    }

    @Test
    fun `sidebar contains metadata proposals link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/metadata-proposals\""), "Sidebar should have /metadata-proposals link")
    }
}
