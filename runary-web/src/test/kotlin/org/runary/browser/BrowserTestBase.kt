package org.runary.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Cookie
import org.runary.integration.IntegrationTestBase
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Base class for browser-based UI tests using Playwright.
 *
 * Annotated with @Tag("browser") so they are excluded from the default surefire run.
 * Run with: mvn test -P browser-tests
 *
 * First-time setup: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI
 *                       -D exec.args="install chromium" -D exec.classpathScope=test
 * Or just run: mvn test -P browser-tests  (the profile installs browsers automatically)
 *
 * Each browser test must complete within 45 seconds or it is killed.
 */
@Tag("browser")
@Timeout(value = 45, unit = TimeUnit.SECONDS)
abstract class BrowserTestBase : IntegrationTestBase() {
    companion object {
        @Volatile private var initialized = false

        lateinit var browser: Browser
        private lateinit var playwright: Playwright

        @Volatile private var server: Http4kServer? = null

        @Volatile private var _baseUrl: String? = null
        val baseUrl: String get() = _baseUrl ?: error("Server not started")

        @BeforeAll @JvmStatic
        fun initPlaywright() {
            if (!initialized) {
                synchronized(BrowserTestBase::class.java) {
                    if (!initialized) {
                        playwright = Playwright.create()
                        browser =
                            playwright.chromium().launch(
                                BrowserType.LaunchOptions().setHeadless(true),
                            )
                        initialized = true
                        Runtime.getRuntime().addShutdownHook(
                            Thread {
                                runCatching { server?.stop() }
                                runCatching { browser.close() }
                                runCatching { playwright.close() }
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts the Jetty server on first call (shared across all browser test classes).
     * Also builds this.app so the helper methods (registerAndGetToken etc.) work.
     */
    @BeforeEach
    override fun setupApp() {
        super.setupApp()
        if (_baseUrl == null) {
            synchronized(BrowserTestBase::class.java) {
                if (_baseUrl == null) {
                    server = app.asServer(Jetty(0))
                    server!!.start()
                    _baseUrl = "http://localhost:${server!!.port()}"
                }
            }
        }
    }

    /** Opens a new browser page already authenticated as a newly-registered user. */
    protected fun newAuthenticatedPage(prefix: String = "bt"): Pair<Page, String> {
        val token = registerAndGetToken(prefix)
        val page = newPage()
        page.context().addCookies(
            listOf(
                Cookie("token", token)
                    .setDomain("localhost")
                    .setPath("/"),
            ),
        )
        return Pair(page, token)
    }

    /** Opens a fresh page with Playwright timeouts pre-configured. */
    protected fun newPage(): Page {
        val page = browser.newPage()
        // Fail fast on any selector wait or navigation — avoids hanging CI
        page.setDefaultTimeout(10_000.0)
        page.setDefaultNavigationTimeout(15_000.0)
        return page
    }

    /** Uploads bytes to the book's file endpoint (via the in-process app, not the browser). */
    protected fun uploadFile(
        token: String,
        bookId: String,
        filename: String,
        bytes: ByteArray,
    ) {
        app(
            org.http4k.core
                .Request(org.http4k.core.Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", filename)
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(bytes), bytes.size.toLong())),
        )
    }

    /** Builds a minimal but structurally valid EPUB zip. */
    protected fun minimalEpubBytes(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            // mimetype — must be first, uncompressed
            zip.setMethod(java.util.zip.ZipOutputStream.STORED)
            val mimeEntry = java.util.zip.ZipEntry("mimetype")
            val mimeBytes = "application/epub+zip".toByteArray()
            mimeEntry.size = mimeBytes.size.toLong()
            mimeEntry.compressedSize = mimeBytes.size.toLong()
            mimeEntry.crc =
                java.util.zip
                    .CRC32()
                    .also { it.update(mimeBytes) }
                    .value
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.setMethod(java.util.zip.ZipOutputStream.DEFLATED)

            zip.putNextEntry(java.util.zip.ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Browser Test Book</dc:title>
    <dc:identifier id="bookid">test-epub-001</dc:identifier>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/chapter1.xhtml"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>Chapter One</title></head>
  <body><h1>Chapter One</h1><p>This is the first paragraph.</p></body>
</html>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(java.util.zip.ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="test-epub-001"/></head>
  <docTitle><text>Browser Test Book</text></docTitle>
  <navMap>
    <navPoint id="navPoint-1" playOrder="1">
      <navLabel><text>Chapter One</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".toByteArray(),
            )
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
