package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.booktower.services.AnalyticsService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.EpubMetadataService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubMetadataIntegrationTest : IntegrationTestBase() {
    private lateinit var epubMetadataService: EpubMetadataService
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var libId: String

    @BeforeEach
    fun setupServices() {
        val jdbi = TestFixture.database.getJdbi()
        val config = TestFixture.config
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        bookService = BookService(jdbi, analyticsService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)

        val result =
            authService.register(
                CreateUserRequest("epub_${System.nanoTime()}", "epub_${System.nanoTime()}@test.com", "password123"),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        libId = libraryService.createLibrary(userId, CreateLibraryRequest("EPUB Lib", "./data/epub-${System.nanoTime()}")).id
    }

    // ── Helper to build minimal valid EPUB ZIPs ────────────────────────────────

    private fun makeEpub(
        dir: Path,
        name: String,
        title: String,
        author: String,
        description: String? = null,
        coverBytes: ByteArray? = null,
        coverExt: String = "jpg",
    ): java.io.File {
        val file = dir.resolve(name).toFile()
        ZipOutputStream(file.outputStream()).use { zos ->
            // mimetype (must be first, uncompressed)
            zos.setMethod(ZipOutputStream.STORED)
            val mimetypeBytes = "application/epub+zip".toByteArray()
            val mimetypeEntry =
                ZipEntry("mimetype").also {
                    it.size = mimetypeBytes.size.toLong()
                    it.compressedSize = mimetypeBytes.size.toLong()
                    it.crc =
                        java.util.zip
                            .CRC32()
                            .also { c -> c.update(mimetypeBytes) }
                            .value
                }
            zos.putNextEntry(mimetypeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            zos.setMethod(ZipOutputStream.DEFLATED)

            // META-INF/container.xml
            val containerXml =
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="EPUB/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray())
            zos.closeEntry()

            // Cover image (if provided)
            val coverManifestItem =
                if (coverBytes != null) {
                    zos.putNextEntry(ZipEntry("EPUB/images/cover.$coverExt"))
                    zos.write(coverBytes)
                    zos.closeEntry()
                    """<item id="cover-image" href="images/cover.$coverExt" media-type="image/${if (coverExt == "jpg") "jpeg" else coverExt}" properties="cover-image"/>"""
                } else {
                    ""
                }

            // EPUB/content.opf
            val descriptionElement =
                if (description != null) {
                    "<dc:description>$description</dc:description>"
                } else {
                    ""
                }
            val opf =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    $descriptionElement
                  </metadata>
                  <manifest>
                    $coverManifestItem
                    <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="toc"/></spine>
                </package>
                """.trimIndent()
            zos.putNextEntry(ZipEntry("EPUB/content.opf"))
            zos.write(opf.toByteArray())
            zos.closeEntry()
        }
        return file
    }

    private fun makeEpubEpub2Cover(
        dir: Path,
        name: String,
        title: String,
        author: String,
    ): java.io.File {
        val file = dir.resolve(name).toFile()
        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.setMethod(ZipOutputStream.DEFLATED)

            val containerXml = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("images/cover.jpg"))
            zos.write(coverBytes)
            zos.closeEntry()

            // EPUB2-style OPF: <meta name="cover" content="cover-img"/>
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title>
    <dc:creator>$author</dc:creator>
    <meta name="cover" content="cover-img"/>
  </metadata>
  <manifest>
    <item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"/>
  </manifest>
  <spine><itemref idref="toc"/></spine>
</package>"""
            zos.putNextEntry(ZipEntry("content.opf"))
            zos.write(opf.toByteArray())
            zos.closeEntry()
        }
        return file
    }

    // ── Service-level unit tests ───────────────────────────────────────────────

    @Test
    fun `extractAndStore reads title and author from EPUB3 OPF`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("Placeholder", null, null, libId)).getOrThrow()
        val epubFile = makeEpub(tmpDir, "test.epub", "My EPUB Title", "Jane Author")

        val meta = epubMetadataService.extractAndStore(book.id, epubFile)

        assertEquals("My EPUB Title", meta.title)
        assertEquals("Jane Author", meta.author)
    }

    @Test
    fun `extractAndStore reads description`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("X", null, null, libId)).getOrThrow()
        val epubFile = makeEpub(tmpDir, "desc.epub", "Title", "Author", description = "A great book.")

        val meta = epubMetadataService.extractAndStore(book.id, epubFile)

        assertEquals("A great book.", meta.description)
    }

    @Test
    fun `extractAndStore persists title into DB`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("Old Title", null, null, libId)).getOrThrow()
        val epubFile = makeEpub(tmpDir, "persist.epub", "New EPUB Title", "Author")

        epubMetadataService.extractAndStore(book.id, epubFile)

        val updated = bookService.getBook(userId, UUID.fromString(book.id))
        assertEquals("New EPUB Title", updated?.title)
    }

    @Test
    fun `extractAndStore extracts EPUB3 cover image`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("Cover Book", null, null, libId)).getOrThrow()
        val coverData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xAB.toByte())
        val epubFile = makeEpub(tmpDir, "cover.epub", "Title", "Author", coverBytes = coverData)

        val meta = epubMetadataService.extractAndStore(book.id, epubFile)

        assertNotNull(meta.coverPath)
        assertTrue(meta.coverPath!!.contains(book.id))
    }

    @Test
    fun `extractAndStore extracts EPUB2 cover via meta name=cover`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("EPUB2 Book", null, null, libId)).getOrThrow()
        val epubFile = makeEpubEpub2Cover(tmpDir, "epub2.epub", "EPUB2 Title", "Author")

        val meta = epubMetadataService.extractAndStore(book.id, epubFile)

        assertEquals("EPUB2 Title", meta.title)
        assertNotNull(meta.coverPath)
    }

    @Test
    fun `extractAndStore returns null metadata for non-EPUB file`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("Bad", null, null, libId)).getOrThrow()
        val badFile = tmpDir.resolve("fake.epub").toFile()
        badFile.writeText("this is not an epub")

        val meta = epubMetadataService.extractAndStore(book.id, badFile)

        assertNull(meta.title)
        assertNull(meta.author)
        assertNull(meta.coverPath)
    }

    @Test
    fun `extractAndStore returns null metadata for missing file`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("Missing", null, null, libId)).getOrThrow()
        val missingFile = tmpDir.resolve("missing.epub").toFile()

        val meta = epubMetadataService.extractAndStore(book.id, missingFile)

        assertNull(meta.title)
    }

    @Test
    fun `extractAndStore strips HTML from description`(
        @TempDir tmpDir: Path,
    ) {
        val book = bookService.createBook(userId, CreateBookRequest("HTML Book", null, null, libId)).getOrThrow()
        val epubFile = makeEpub(tmpDir, "html.epub", "Title", "Author", description = "<p>Bold <b>text</b></p>")

        val meta = epubMetadataService.extractAndStore(book.id, epubFile)

        assertEquals("Bold text", meta.description)
    }

    // ── Upload-path integration test ───────────────────────────────────────────

    @Test
    fun `uploading EPUB via API triggers metadata extraction`(
        @TempDir tmpDir: Path,
    ) {
        val token = registerAndGetToken("epubupload")
        val libId2 = createLibrary(token)
        val bookId = createBook(token, libId2, "Upload EPUB Test")

        val epubFile = makeEpub(tmpDir, "upload.epub", "Uploaded Title", "Upload Author")
        val epubBytes = epubFile.readBytes()

        val uploadResp =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("X-Filename", "upload.epub")
                    .body(epubBytes.inputStream()),
            )
        assertEquals(Status.OK, uploadResp.status)
    }
}
