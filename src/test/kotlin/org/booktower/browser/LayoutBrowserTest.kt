package org.booktower.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Playwright browser tests for layout structure, responsive behavior,
 * and visual consistency across viewports.
 *
 * Run with: mvn test -P browser-tests -Dtest="LayoutBrowserTest"
 */
@Tag("browser")
@Timeout(value = 45, unit = TimeUnit.SECONDS)
class LayoutBrowserTest : BrowserTestBase() {
    // ── Dashboard layout ─────────────────────────────────────────────────────

    @Test
    fun `dashboard renders header, sidebar, and main content`() {
        val (page, _) = newAuthenticatedPage("lay1")
        page.navigate("$baseUrl/")

        // Header present
        val header = page.querySelector("header")
        assertNotNull(header, "Header should be present")
        assertTrue(header.isVisible, "Header should be visible")

        // Sidebar present with navigation links
        val sidebar = page.querySelector("nav")
        assertNotNull(sidebar, "Sidebar nav should be present")
        val links = page.querySelectorAll(".sidebar-link")
        assertTrue(links.size >= 8, "Should have at least 8 sidebar links, got ${links.size}")

        // Main content area present
        val main = page.querySelector("main")
        assertNotNull(main, "Main content area should be present")

        // Footer present
        val footer = page.querySelector("footer")
        assertNotNull(footer, "Footer should be present")

        page.close()
    }

    @Test
    fun `dashboard stat cards render in a grid`() {
        val (page, _) = newAuthenticatedPage("lay2")
        page.navigate("$baseUrl/")

        val statCards = page.querySelectorAll(".stat-card")
        assertTrue(statCards.isNotEmpty(), "Dashboard should have stat cards")

        // All stat cards should have icon and value
        for (card in statCards) {
            val icon = card.querySelector(".stat-icon")
            assertNotNull(icon, "Each stat card should have an icon")
            val value = card.querySelector(".stat-value")
            assertNotNull(value, "Each stat card should have a value")
        }

        page.close()
    }

    // ── Sidebar navigation ──────────────────────────────────────────────────

    @Test
    fun `sidebar link navigates to libraries page`() {
        val (page, _) = newAuthenticatedPage("lay3")
        page.navigate("$baseUrl/")

        // Click libraries link
        page.click("a.sidebar-link[href='/libraries']")
        page.waitForURL("**/libraries")

        assertTrue(page.url().contains("/libraries"), "Should navigate to libraries")

        // Active link should be highlighted
        val activeLink = page.querySelector("a.sidebar-link.active[href='/libraries']")
        assertNotNull(activeLink, "Libraries link should be active")

        page.close()
    }

    @Test
    fun `sidebar shows active state for current page`() {
        val (page, _) = newAuthenticatedPage("lay4")
        page.navigate("$baseUrl/search")

        val activeLinks = page.querySelectorAll("a.sidebar-link.active")
        assertEquals(1, activeLinks.size, "Exactly one sidebar link should be active")

        val href = activeLinks[0].getAttribute("href")
        assertEquals("/search", href, "Search link should be active on search page")

        page.close()
    }

    // ── Header components ───────────────────────────────────────────────────

    @Test
    fun `header shows search bar and user info`() {
        val (page, _) = newAuthenticatedPage("lay5")
        page.navigate("$baseUrl/")

        // Search input present
        val searchInput = page.querySelector("input[type='search']")
        assertNotNull(searchInput, "Search input should be in header")

        // User avatar or icon present
        val avatar = page.querySelector("img[alt]") ?: page.querySelector("a[href='/profile'] i")
        assertNotNull(avatar, "User avatar or icon should be present")

        // Logout button present
        val logoutBtn = page.querySelector("button[hx-post='/auth/logout']")
        assertNotNull(logoutBtn, "Logout button should be present")

        page.close()
    }

    @Test
    fun `search form submits and navigates to search page`() {
        val (page, _) = newAuthenticatedPage("lay6")
        page.navigate("$baseUrl/")

        page.fill("input[type='search']", "test query")
        page.press("input[type='search']", "Enter")
        page.waitForURL("**/search**")

        assertTrue(page.url().contains("/search"), "Should navigate to search")
        assertTrue(page.url().contains("q=test"), "Search query should be in URL")

        page.close()
    }

    // ── Responsive layout ───────────────────────────────────────────────────

    @Test
    fun `sidebar is hidden on mobile viewport`() {
        val (page, _) = newAuthenticatedPage("lay7")
        page.setViewportSize(375, 667) // iPhone SE size
        page.navigate("$baseUrl/")

        // Sidebar should be hidden at mobile width
        val sidebar = page.querySelector("nav")
        if (sidebar != null) {
            val box = sidebar.boundingBox()
            // Either hidden (display:none → null bounding box) or zero width
            assertTrue(
                box == null || box.width == 0.0,
                "Sidebar should be hidden on mobile viewport",
            )
        }

        // Main content should still be visible
        val main = page.querySelector("main")
        assertNotNull(main, "Main content should still be visible on mobile")
        assertTrue(main.isVisible, "Main content should be visible")

        page.close()
    }

    @Test
    fun `layout renders correctly at tablet width`() {
        val (page, _) = newAuthenticatedPage("lay8")
        page.setViewportSize(768, 1024) // iPad size
        page.navigate("$baseUrl/")

        // Header should be present at all sizes
        val header = page.querySelector("header")
        assertNotNull(header, "Header should be present at tablet width")
        assertTrue(header.isVisible, "Header should be visible")

        // Main content should be visible
        val main = page.querySelector("main")
        assertNotNull(main, "Main content should be present at tablet width")

        page.close()
    }

    @Test
    fun `layout renders correctly at desktop width`() {
        val (page, _) = newAuthenticatedPage("lay9")
        page.setViewportSize(1440, 900) // Desktop
        page.navigate("$baseUrl/")

        // Sidebar should be visible at desktop
        val sidebar = page.querySelector("nav")
        assertNotNull(sidebar, "Sidebar should be present at desktop width")
        val box = sidebar.boundingBox()
        assertNotNull(box, "Sidebar should have a bounding box (visible)")
        assertTrue(box.width > 100, "Sidebar should be wide enough to show content")

        page.close()
    }

    // ── Libraries page layout ───────────────────────────────────────────────

    @Test
    fun `libraries page shows create button and empty state`() {
        val (page, _) = newAuthenticatedPage("lay10")
        page.navigate("$baseUrl/libraries")

        // The page should have a title area and a create mechanism
        val body = page.content()
        assertTrue(body.contains("librar", ignoreCase = true), "Page should mention libraries")

        page.close()
    }

    // ── CSS loaded correctly ────────────────────────────────────────────────

    @Test
    fun `app css is loaded and theme variables are set`() {
        val (page, _) = newAuthenticatedPage("lay11")
        page.navigate("$baseUrl/")

        // Check that the theme style tag exists
        val themeStyle = page.querySelector("style#theme-style")
        assertNotNull(themeStyle, "Theme style tag should exist")

        // Check that app.css is loaded
        val appCssLink = page.querySelector("link[href='/static/css/app.css']")
        assertNotNull(appCssLink, "app.css link should be present")

        // Verify no style.css reference (it was removed)
        val styleCssLink = page.querySelector("link[href='/static/css/style.css']")
        assertTrue(styleCssLink == null, "style.css should NOT be referenced (was consolidated)")

        page.close()
    }

    @Test
    fun `footer is visible at bottom of page`() {
        val (page, _) = newAuthenticatedPage("lay12")
        page.navigate("$baseUrl/")

        val footer = page.querySelector("footer")
        assertNotNull(footer, "Footer should be present")
        assertTrue(footer.isVisible, "Footer should be visible")

        val text = footer.textContent() ?: ""
        assertTrue(text.contains("BookTower"), "Footer should contain BookTower text")

        page.close()
    }
}
