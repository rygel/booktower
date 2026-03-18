package org.booktower.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FilenameMetadataServiceTest {
    private fun parse(name: String) = FilenameMetadataService.parse(name)

    @Test
    fun `title only`() {
        val r = parse("Dune")
        assertEquals("Dune", r.title)
        assertNull(r.author)
        assertNull(r.series)
    }

    @Test
    fun `author dash title`() {
        val r = parse("Frank Herbert - Dune")
        assertEquals("Dune", r.title)
        assertEquals("Frank Herbert", r.author)
        assertNull(r.series)
    }

    @Test
    fun `title with series in parentheses`() {
        val r = parse("Dune (Dune Chronicles 1)")
        assertEquals("Dune", r.title)
        assertNull(r.author)
        assertEquals("Dune Chronicles", r.series)
        assertEquals(1f, r.seriesIndex)
    }

    @Test
    fun `author dash title with series in parentheses`() {
        val r = parse("Frank Herbert - Dune (Dune Chronicles 1)")
        assertEquals("Dune", r.title)
        assertEquals("Frank Herbert", r.author)
        assertEquals("Dune Chronicles", r.series)
        assertEquals(1f, r.seriesIndex)
    }

    @Test
    fun `series with decimal index`() {
        val r = parse("Brandon Sanderson - The Way of Kings (The Stormlight Archive 1.5)")
        assertEquals("The Way of Kings", r.title)
        assertEquals("Brandon Sanderson", r.author)
        assertEquals("The Stormlight Archive", r.series)
        assertEquals(1.5f, r.seriesIndex)
    }

    @Test
    fun `series with leading zero index`() {
        val r = parse("Author - Title (Series 02)")
        assertEquals("Title", r.title)
        assertEquals("Author", r.author)
        assertEquals("Series", r.series)
        assertEquals(2f, r.seriesIndex)
    }

    @Test
    fun `author dash series number dash title pattern`() {
        val r = parse("Frank Herbert - Dune Chronicles 1 - Dune")
        assertEquals("Dune", r.title)
        assertEquals("Frank Herbert", r.author)
        assertEquals("Dune Chronicles", r.series)
        assertEquals(1f, r.seriesIndex)
    }

    @Test
    fun `series with brackets`() {
        val r = parse("Title [Series 3]")
        assertEquals("Title", r.title)
        assertEquals("Series", r.series)
        assertEquals(3f, r.seriesIndex)
    }

    @Test
    fun `title with Book N in parentheses`() {
        val r = parse("A Title (My Series, Book 2)")
        assertEquals("A Title", r.title)
        assertEquals("My Series", r.series)
        assertEquals(2f, r.seriesIndex)
    }

    @Test
    fun `extract strips file extension`() {
        val r = FilenameMetadataService.extract("/path/to/Frank Herbert - Dune.epub")
        assertEquals("Dune", r.title)
        assertEquals("Frank Herbert", r.author)
    }

    @Test
    fun `whitespace is trimmed`() {
        val r = parse("  Author  -  Title  ")
        assertEquals("Title", r.title)
        assertEquals("Author", r.author)
    }
}
