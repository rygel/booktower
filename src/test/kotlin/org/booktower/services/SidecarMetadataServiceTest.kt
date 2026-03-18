package org.booktower.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SidecarMetadataServiceTest {
    @TempDir
    lateinit var tmp: Path

    private fun file(
        name: String,
        content: String,
    ): File = tmp.resolve(name).toFile().also { it.writeText(content) }

    // ── OPF tests ─────────────────────────────────────────────────────────────

    @Test
    fun `parseOpf extracts title author publisher and date`() {
        val opf =
            file(
                "book.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Dune</dc:title>
    <dc:creator>Frank Herbert</dc:creator>
    <dc:publisher>Ace Books</dc:publisher>
    <dc:date>1965-08-01</dc:date>
    <dc:description>A science fiction epic.</dc:description>
    <dc:identifier>9780441013593</dc:identifier>
  </metadata>
</package>""",
            )
        val r = SidecarMetadataService.parseOpf(opf)!!
        assertEquals("Dune", r.title)
        assertEquals("Frank Herbert", r.author)
        assertEquals("Ace Books", r.publisher)
        assertEquals("1965", r.publishedDate)
        assertEquals("A science fiction epic.", r.description)
        assertEquals("9780441013593", r.isbn)
        assertEquals("opf", r.source)
    }

    @Test
    fun `parseOpf handles multiple creators joined by comma`() {
        val opf =
            file(
                "multi.opf",
                """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Co-authored</dc:title>
    <dc:creator>Author One</dc:creator>
    <dc:creator>Author Two</dc:creator>
  </metadata>
</package>""",
            )
        val r = SidecarMetadataService.parseOpf(opf)!!
        assertEquals("Author One, Author Two", r.author)
    }

    @Test
    fun `parseOpf returns null for malformed XML`() {
        val opf = file("bad.opf", "not xml at all <<<")
        assertNull(SidecarMetadataService.parseOpf(opf))
    }

    // ── NFO tests ─────────────────────────────────────────────────────────────

    @Test
    fun `parseNfo extracts title author and year`() {
        val nfo =
            file(
                "book.nfo",
                """<?xml version="1.0"?>
<book>
  <title>Foundation</title>
  <author>Isaac Asimov</author>
  <year>1951</year>
  <publisher>Gnome Press</publisher>
  <plot>Galactic empire falls.</plot>
</book>""",
            )
        val r = SidecarMetadataService.parseNfo(nfo)!!
        assertEquals("Foundation", r.title)
        assertEquals("Isaac Asimov", r.author)
        assertEquals("1951", r.publishedDate)
        assertEquals("Gnome Press", r.publisher)
        assertEquals("Galactic empire falls.", r.description)
        assertEquals("nfo", r.source)
    }

    @Test
    fun `parseNfo falls back to creator for author`() {
        val nfo =
            file(
                "creator.nfo",
                """<?xml version="1.0"?>
<book><title>T</title><creator>C. Author</creator></book>""",
            )
        val r = SidecarMetadataService.parseNfo(nfo)!!
        assertEquals("C. Author", r.author)
    }

    // ── read() discovery tests ────────────────────────────────────────────────

    @Test
    fun `read finds same-name opf next to book file`() {
        file(
            "MyBook.opf",
            """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>My OPF Book</dc:title>
  </metadata>
</package>""",
        )
        val epub = file("MyBook.epub", "fake")
        val r = SidecarMetadataService.read(epub.absolutePath)!!
        assertEquals("My OPF Book", r.title)
    }

    @Test
    fun `read finds same-name nfo next to book file`() {
        file("Story.nfo", """<?xml version="1.0"?><book><title>NFO Story</title></book>""")
        val book = file("Story.pdf", "fake")
        val r = SidecarMetadataService.read(book.absolutePath)!!
        assertEquals("NFO Story", r.title)
    }

    @Test
    fun `read returns null when no sidecar exists`() {
        val book = file("NoSidecar.epub", "fake")
        assertNull(SidecarMetadataService.read(book.absolutePath))
    }

    @Test
    fun `read prefers opf over nfo when both exist`() {
        file(
            "Both.opf",
            """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>OPF Wins</dc:title></metadata>
</package>""",
        )
        file("Both.nfo", """<?xml version="1.0"?><book><title>NFO Loses</title></book>""")
        val book = file("Both.epub", "fake")
        val r = SidecarMetadataService.read(book.absolutePath)!!
        assertEquals("OPF Wins", r.title)
    }
}
