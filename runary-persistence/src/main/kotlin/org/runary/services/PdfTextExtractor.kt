package org.runary.services

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import java.io.File

private val pdfExtractLog = LoggerFactory.getLogger("runary.PdfTextExtractor")

object PdfTextExtractor {
    fun extract(file: File): String? =
        try {
            Loader.loadPDF(file).use { doc ->
                PDFTextStripper().getText(doc).take(2_000_000)
            }
        } catch (e: Exception) {
            pdfExtractLog.warn("PDF text extraction failed for ${file.name}: ${e.message}")
            null
        }
}
