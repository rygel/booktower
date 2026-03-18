package org.booktower.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComicServiceTest {
    private val service = ComicService()

    private fun makeCbz(
        dir: Path,
        name: String,
        entries: Map<String, ByteArray>,
    ): Path {
        val cbz = dir.resolve(name)
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            entries.forEach { (entryName, data) ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return cbz
    }

    @Test
    fun `non-image files inside CBZ are filtered out`(
        @TempDir tmpDir: Path,
    ) {
        val cbz =
            makeCbz(
                tmpDir,
                "mixed.cbz",
                mapOf(
                    "page1.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                    "readme.txt" to "not an image".toByteArray(),
                    "page2.png" to byteArrayOf(0x89.toByte(), 0x50.toByte()),
                    "thumbs.db" to byteArrayOf(0x00),
                ),
            )
        val pages = service.listPages(cbz.toString())
        assertEquals(2, pages.size)
        assertTrue(pages.all { it.name.endsWith(".jpg") || it.name.endsWith(".png") })
    }

    @Test
    fun `directory entries inside CBZ are excluded from page count`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = tmpDir.resolve("dirs.cbz")
        ZipOutputStream(cbz.toFile().outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("subdir/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("subdir/001.jpg"))
            zos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            zos.closeEntry()
        }
        assertEquals(1, service.getPageCount(cbz.toString()))
    }

    @Test
    fun `empty CBZ has page count zero`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = makeCbz(tmpDir, "empty.cbz", emptyMap())
        assertEquals(0, service.getPageCount(cbz.toString()))
    }

    @Test
    fun `CBZ with only non-image files has page count zero`(
        @TempDir tmpDir: Path,
    ) {
        val cbz =
            makeCbz(
                tmpDir,
                "noimg.cbz",
                mapOf(
                    "info.xml" to "<xml/>".toByteArray(),
                    "cover.pdf" to byteArrayOf(0x25, 0x50),
                ),
            )
        assertEquals(0, service.getPageCount(cbz.toString()))
    }

    @Test
    fun `MIME type detected correctly for jpg`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = makeCbz(tmpDir, "mime.cbz", mapOf("page.jpg" to byteArrayOf(0xFF.toByte())))
        val pages = service.listPages(cbz.toString())
        assertEquals("image/jpeg", pages[0].contentType)
    }

    @Test
    fun `MIME type detected correctly for png`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = makeCbz(tmpDir, "mimepng.cbz", mapOf("page.png" to byteArrayOf(0x89.toByte())))
        val pages = service.listPages(cbz.toString())
        assertEquals("image/png", pages[0].contentType)
    }

    @Test
    fun `MIME type detected correctly for webp`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = makeCbz(tmpDir, "mimewebp.cbz", mapOf("page.webp" to byteArrayOf(0x52)))
        val pages = service.listPages(cbz.toString())
        assertEquals("image/webp", pages[0].contentType)
    }

    @Test
    fun `page indices are zero-based and sequential`(
        @TempDir tmpDir: Path,
    ) {
        val cbz =
            makeCbz(
                tmpDir,
                "idx.cbz",
                mapOf(
                    "001.jpg" to byteArrayOf(0x01),
                    "002.jpg" to byteArrayOf(0x02),
                    "003.jpg" to byteArrayOf(0x03),
                ),
            )
        val pages = service.listPages(cbz.toString())
        assertEquals(listOf(0, 1, 2), pages.map { it.index })
    }

    @Test
    fun `getPage returns null for non-existent file`() {
        assertNull(service.getPage("/does/not/exist.cbz", 0))
    }

    @Test
    fun `listPages returns empty for non-existent file`() {
        assertTrue(service.listPages("/does/not/exist.cbz").isEmpty())
    }

    @Test
    fun `listPages returns empty for unsupported extension`(
        @TempDir tmpDir: Path,
    ) {
        val file = tmpDir.resolve("book.zip").toFile()
        file.writeBytes(byteArrayOf(0x50, 0x4B))
        assertTrue(service.listPages(file.absolutePath).isEmpty())
    }

    @Test
    fun `getPage returns correct bytes for page 0`(
        @TempDir tmpDir: Path,
    ) {
        val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xAA.toByte())
        val cbz = makeCbz(tmpDir, "exact.cbz", mapOf("001.jpg" to data))
        val bytes = service.getPage(cbz.toString(), 0)
        assertTrue(bytes != null && bytes.contentEquals(data))
    }

    @Test
    fun `getPage returns null for out-of-range index`(
        @TempDir tmpDir: Path,
    ) {
        val cbz = makeCbz(tmpDir, "range.cbz", mapOf("001.jpg" to byteArrayOf(0x00)))
        assertNull(service.getPage(cbz.toString(), 5))
    }
}
