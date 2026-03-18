package org.booktower.services

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converts FB2 (FictionBook 2.x) XML files to a self-contained HTML document
 * suitable for embedding in an iframe reader.
 *
 * Uses only the JDK's built-in JAXP parser — no external dependencies.
 */
class Fb2ReaderService {
    fun toHtml(file: File): String {
        val dbf =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        val doc = dbf.newDocumentBuilder().parse(file)
        doc.documentElement.normalize()

        val title = extractTitle(doc)
        val author = extractAuthor(doc)

        val sb = StringBuilder()
        sb.append(
            """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>
  body{font-family:Georgia,"Times New Roman",serif;max-width:42rem;margin:0 auto;padding:2rem 1.5rem 4rem;line-height:1.8;color:#222;background:#fafaf8;font-size:1.05rem}
  h1{font-size:1.75rem;border-bottom:1px solid #ddd;padding-bottom:0.5rem;margin-bottom:0.25rem}
  .meta{color:#777;font-size:0.95rem;margin-bottom:2.5rem}
  section{margin-bottom:1.5rem}
  h2{font-size:1.3rem;margin-top:2.5rem;margin-bottom:0.75rem}
  h3{font-size:1.1rem;margin-top:1.5rem;margin-bottom:0.5rem}
  p{margin:0 0 0.6rem 0;text-align:justify}
  p+p{text-indent:1.5em}
  .epigraph{border-left:3px solid #ccc;margin:1.5rem 0;padding:0.5rem 1rem;color:#555;font-style:italic}
  .poem{font-style:italic;margin:1.5rem 2rem}
  .poem p{text-indent:0}
  strong{font-weight:700}
  em{font-style:italic}
</style>
</head>
<body>
""",
        )

        if (title.isNotBlank()) sb.append("<h1>${escapeHtml(title)}</h1>\n")
        if (author.isNotBlank()) sb.append("<p class=\"meta\">${escapeHtml(author)}</p>\n")

        val bodies = doc.getElementsByTagName("body")
        for (i in 0 until bodies.length) {
            val body = bodies.item(i)
            if (body is Element) processSection(body, sb, 0)
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    // ── Node processors ───────────────────────────────────────────────────────

    private fun processSection(
        node: Node,
        sb: StringBuilder,
        depth: Int,
    ) {
        if (node !is Element) return
        when (node.nodeName.substringAfterLast(':')) {
            "body" -> node.childNodes.forEach { processSection(it, sb, depth) }
            "section" -> {
                sb.append("<section>")
                node.childNodes.forEach { processSection(it, sb, depth + 1) }
                sb.append("</section>")
            }
            "title" -> {
                val tag = if (depth < 1) "h2" else "h3"
                sb.append("<$tag>")
                node.childNodes.forEach { processInline(it, sb) }
                sb.append("</$tag>\n")
            }
            "p" -> {
                sb.append("<p>")
                node.childNodes.forEach { processInline(it, sb) }
                sb.append("</p>\n")
            }
            "empty-line" -> sb.append("<br>\n")
            "epigraph" -> {
                sb.append("<div class=\"epigraph\">")
                node.childNodes.forEach { processSection(it, sb, depth) }
                sb.append("</div>\n")
            }
            "poem" -> {
                sb.append("<div class=\"poem\">")
                node.childNodes.forEach { child ->
                    if (child is Element && child.nodeName.substringAfterLast(':') == "stanza") {
                        child.childNodes.forEach { v ->
                            if (v is Element && v.nodeName.substringAfterLast(':') == "v") {
                                sb.append("<p>")
                                v.childNodes.forEach { processInline(it, sb) }
                                sb.append("</p>\n")
                            }
                        }
                    }
                }
                sb.append("</div>\n")
            }
            "cite" -> {
                sb.append("<blockquote>")
                node.childNodes.forEach { processSection(it, sb, depth) }
                sb.append("</blockquote>\n")
            }
            // skip binary, description, annotation, etc.
        }
    }

    private fun processInline(
        node: Node,
        sb: StringBuilder,
    ) {
        when (node.nodeType) {
            Node.TEXT_NODE -> sb.append(escapeHtml(node.nodeValue ?: ""))
            Node.ELEMENT_NODE -> {
                val tag = (node as Element).nodeName.substringAfterLast(':')
                when (tag) {
                    "strong" -> {
                        sb.append("<strong>")
                        node.childNodes.forEach { processInline(it, sb) }
                        sb.append("</strong>")
                    }
                    "emphasis" -> {
                        sb.append("<em>")
                        node.childNodes.forEach { processInline(it, sb) }
                        sb.append("</em>")
                    }
                    "p" -> {
                        sb.append("<p>")
                        node.childNodes.forEach { processInline(it, sb) }
                        sb.append("</p>")
                    }
                    // render link text only
                    "a" -> node.childNodes.forEach { processInline(it, sb) }
                    else -> node.childNodes.forEach { processInline(it, sb) }
                }
            }
        }
    }

    // ── Metadata helpers ──────────────────────────────────────────────────────

    private fun extractTitle(doc: org.w3c.dom.Document): String {
        val ti = doc.getElementsByTagName("title-info")
        if (ti.length == 0) return ""
        val bt = (ti.item(0) as Element).getElementsByTagName("book-title")
        return if (bt.length > 0) bt.item(0).textContent.trim() else ""
    }

    private fun extractAuthor(doc: org.w3c.dom.Document): String {
        val ti = doc.getElementsByTagName("title-info")
        if (ti.length == 0) return ""
        val authorEls = (ti.item(0) as Element).getElementsByTagName("author")
        if (authorEls.length == 0) return ""
        val a = authorEls.item(0) as Element
        return listOf("first-name", "middle-name", "last-name")
            .map { tag -> a.getElementsByTagName(tag).let { if (it.length > 0) it.item(0).textContent.trim() else "" } }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun org.w3c.dom.NodeList.forEach(action: (Node) -> Unit) {
        for (i in 0 until length) action(item(i))
    }
}
