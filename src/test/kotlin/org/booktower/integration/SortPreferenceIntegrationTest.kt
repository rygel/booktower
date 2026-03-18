package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SortPreferenceIntegrationTest : IntegrationTestBase() {
    @Test
    fun `library page defaults to TITLE sort with no preference set`() {
        val token = registerAndGetToken("sortpref1")
        val libId = createLibrary(token)
        createBook(token, libId, "Zebra Book")
        createBook(token, libId, "Apple Book")

        // No sort param — should default to TITLE
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertEquals(Status.OK, app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).status)
        val applePos = body.indexOf("Apple Book")
        val zebraPos = body.indexOf("Zebra Book")
        assertTrue(applePos < zebraPos, "Default sort should be TITLE (Apple before Zebra)")
    }

    @Test
    fun `visiting library with sort param saves the preference`() {
        val token = registerAndGetToken("sortpref2")
        val libId = createLibrary(token)

        // Visit with explicit sort=ADDED — saves preference
        val resp1 = app(Request(Method.GET, "/libraries/$libId?sort=ADDED").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp1.status)

        // Visit without sort param — should use saved preference (ADDED)
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        // The ADDED option should appear as selected
        val addedOptionIdx = body.indexOf("value=\"ADDED\"")
        assertTrue(addedOptionIdx >= 0, "ADDED option must be present")
        val snippet = body.substring(addedOptionIdx, minOf(addedOptionIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "ADDED option should be selected after saving preference: $snippet")
    }

    @Test
    fun `saved sort preference persists across library pages`() {
        val token = registerAndGetToken("sortpref3")
        val libId1 = createLibrary(token)
        val libId2 = createLibrary(token)

        // Save AUTHOR preference via lib1
        app(Request(Method.GET, "/libraries/$libId1?sort=AUTHOR").header("Cookie", "token=$token"))

        // Visit lib2 without sort param — should use saved preference
        val body = app(Request(Method.GET, "/libraries/$libId2").header("Cookie", "token=$token")).bodyString()
        val authorOptionIdx = body.indexOf("value=\"AUTHOR\"")
        assertTrue(authorOptionIdx >= 0, "AUTHOR option must be present in lib2")
        val snippet = body.substring(authorOptionIdx, minOf(authorOptionIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "AUTHOR preference should be applied to other library pages: $snippet")
    }

    @Test
    fun `changing sort updates the saved preference`() {
        val token = registerAndGetToken("sortpref4")
        val libId = createLibrary(token)

        // First save ADDED
        app(Request(Method.GET, "/libraries/$libId?sort=ADDED").header("Cookie", "token=$token"))

        // Then change to AUTHOR
        app(Request(Method.GET, "/libraries/$libId?sort=AUTHOR").header("Cookie", "token=$token"))

        // Now without param — AUTHOR should be selected
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        val authorIdx = body.indexOf("value=\"AUTHOR\"")
        assertTrue(authorIdx >= 0)
        val snippet = body.substring(authorIdx, minOf(authorIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "AUTHOR should be the latest saved preference: $snippet")
    }

    @Test
    fun `invalid sort param does not overwrite existing preference`() {
        val token = registerAndGetToken("sortpref5")
        val libId = createLibrary(token)

        // Save AUTHOR preference first
        app(Request(Method.GET, "/libraries/$libId?sort=AUTHOR").header("Cookie", "token=$token"))

        // Visit with invalid sort — should not overwrite
        app(Request(Method.GET, "/libraries/$libId?sort=INVALID").header("Cookie", "token=$token"))

        // Without sort param — AUTHOR should still be selected
        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        val authorIdx = body.indexOf("value=\"AUTHOR\"")
        assertTrue(authorIdx >= 0)
        val snippet = body.substring(authorIdx, minOf(authorIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "AUTHOR preference should not be replaced by invalid sort: $snippet")
    }

    @Test
    fun `sort preference is per-user not shared`() {
        val token1 = registerAndGetToken("sortpref6a")
        val token2 = registerAndGetToken("sortpref6b")
        val libId1 = createLibrary(token1)
        val libId2 = createLibrary(token2)

        // user1 saves ADDED
        app(Request(Method.GET, "/libraries/$libId1?sort=ADDED").header("Cookie", "token=$token1"))

        // user2 without param — should still default to TITLE, not inherit user1's setting
        val body = app(Request(Method.GET, "/libraries/$libId2").header("Cookie", "token=$token2")).bodyString()
        val titleIdx = body.indexOf("value=\"TITLE\"")
        assertTrue(titleIdx >= 0)
        val snippet = body.substring(titleIdx, minOf(titleIdx + 40, body.length))
        assertTrue(snippet.contains("selected"), "User2 should use default TITLE sort, not user1's ADDED: $snippet")
    }
}
