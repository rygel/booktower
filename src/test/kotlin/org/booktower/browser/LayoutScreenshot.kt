package org.booktower.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import java.nio.file.Paths

fun main() {
    val baseUrl = "http://localhost:9999"

    Playwright.create().use { playwright ->
        val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
        val page = browser.newPage()

        // Collect console errors
        val errors = mutableListOf<String>()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error") errors.add(msg.text())
        }
        page.onPageError { err -> errors.add(err) }

        // Login
        page.navigate("$baseUrl/login")
        page.screenshot(com.microsoft.playwright.Page.ScreenshotOptions()
            .setPath(Paths.get("data/screenshot-login.png"))
            .setFullPage(true))
        println("Login screenshot saved")

        page.fill("input[name='username']", "dev")
        page.fill("input[name='password']", "dev12345")
        page.click("button[type='submit']")
        page.waitForTimeout(2000.0)

        // Libraries page
        page.screenshot(com.microsoft.playwright.Page.ScreenshotOptions()
            .setPath(Paths.get("data/screenshot-libraries.png"))
            .setFullPage(true))
        println("Libraries screenshot saved to data/screenshot-libraries.png")
        println("Current URL: ${page.url()}")
        println("Title: ${page.title()}")

        // Print errors
        if (errors.isNotEmpty()) {
            println("\nConsole errors (${errors.size}):")
            errors.forEach { println("  - $it") }
        } else {
            println("\nNo console errors")
        }

        browser.close()
    }
}
