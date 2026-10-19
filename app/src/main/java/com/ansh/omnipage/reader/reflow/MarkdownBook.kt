package com.ansh.omnipage.reader.reflow

import android.content.Context
import android.net.Uri
import java.io.IOException

// Markdown rendered to simple HTML, split into chapters at H1/H2 headings (or every ~60k chars).
class MarkdownBook private constructor(
    override val title: String?,
    private val titles: List<String>,
    private val bodies: List<String>,
) : ReflowBook() {

    override val spine: List<SpineItem> =
        bodies.indices.map { SpineItem("md/$it.html", bodies[it].length.toLong().coerceAtLeast(1L)) }

    override val toc: List<TocItem> =
        if (titles.size > 1) titles.mapIndexed { i, t -> TocItem(t, i, null, 0) } else emptyList()

    override fun chapterHtml(index: Int): String {
        val i = index.coerceIn(0, bodies.lastIndex)
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>${escape(titles[i])}</title></head>" +
            "<body>${bodies[i]}</body></html>"
    }

    override fun resource(path: String): BookResource? = null

    override fun close() = Unit

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024

        fun open(context: Context, uri: Uri, fallbackTitle: String?): MarkdownBook {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot read this file.")
            if (bytes.size > MAX_BYTES) throw IOException("This Markdown file is too large to open.")

            val text = TxtBook.decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
            var lines = text.split('\n')

            // Skip YAML front matter.
            if (lines.firstOrNull()?.trim() == "---") {
                val end = lines.drop(1).take(60).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
                if (end >= 0) lines = lines.drop(end + 2)
            }

            val converter = Converter()
            converter.convert(lines)
            if (converter.bodies.isEmpty()) throw IOException("This Markdown file is empty.")
            return MarkdownBook(fallbackTitle, converter.titles, converter.bodies)
        }
    }
}

private fun escape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val TRAIL_HASH = Regex("\\s+#+\\s*$")
private val MARKUP = Regex("[*_`]")
private val HR = Regex("^([-*_])(\\s*\\1){2,}$")
private val LIST = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)$")
private val TASK = Regex("^\\[([ xX])]\\s+")
private val TABLE_SEP = Regex("^\\|?[\\s:|-]+\\|?$")
private val CODE = Regex("`([^`]+)`")
private val IMAGE = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")
private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")
private val BOLD_STAR = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
private val BOLD_UNDER = Regex("(?<![A-Za-z0-9])__(?=\\S)(.+?)(?<=\\S)__(?![A-Za-z0-9])")
private val ITALIC_STAR = Regex("\\*(?=\\S)(.+?)(?<=\\S)\\*")
private val ITALIC_UNDER = Regex("(?<![A-Za-z0-9])_(?=\\S)(.+?)(?<=\\S)_(?![A-Za-z0-9])")
private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")

private fun inline(raw: String): String {
    val out = StringBuilder()
    var last = 0
    for (m in CODE.findAll(raw)) {
        out.append(spans(escape(raw.substring(last, m.range.first))))
        out.append("<code>").append(escape(m.groupValues[1])).append("</code>")
        last = m.range.last + 1
    }
    out.append(spans(escape(raw.substring(last))))
    return out.toString()
}

private fun spans(escaped: String): String {
    var t = escaped
    t = IMAGE.replace(t) { m -> m.groupValues[1] } // remote images are blocked in the reader; keep the alt text
    t = LINK.replace(t) { m -> "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>" }
    t = BOLD_STAR.replace(t) { m -> "<strong>${m.groupValues[1]}</strong>" }
    t = BOLD_UNDER.replace(t) { m -> "<strong>${m.groupValues[1]}</strong>" }
    t = ITALIC_STAR.replace(t) { m -> "<em>${m.groupValues[1]}</em>" }
    t = ITALIC_UNDER.replace(t) { m -> "<em>${m.groupValues[1]}</em>" }
    t = STRIKE.replace(t) { m -> "<del>${m.groupValues[1]}</del>" }
    return t
}

private class Converter {
    val titles = ArrayList<String>()
    val bodies = ArrayList<String>()

    private val out = StringBuilder()
    private var chapterTitle: String? = null

    private val para = StringBuilder()
    private val lists = ArrayList<Pair<Int, String>>() // (indent, "ul" | "ol")
    private val quote = ArrayList<String>()
    private val table = ArrayList<String>()
    private val code = StringBuilder()
    private var inCode = false
    private var fence = "```"

    fun convert(lines: List<String>) {
        for (line in lines) accept(line.replace("\t", "    "))
        endAll()
        flush()
    }

    private fun accept(line: String) {
        if (inCode) {
            if (line.trim().startsWith(fence)) {
                out.append("<pre><code>").append(escape(code.toString().trimEnd('\n'))).append("</code></pre>")
                code.setLength(0)
                inCode = false
            } else {
                code.append(line).append('\n')
            }
            return
        }

        val t = line.trim()
        if (t.startsWith("```") || t.startsWith("~~~")) {
            endAll()
            inCode = true
            fence = t.take(3)
            return
        }
        if (t.isEmpty()) {
            endAll()
            return
        }

        val heading = HEADING.matchEntire(t)
        if (heading != null) {
            endAll()
            heading(heading.groupValues[1].length, heading.groupValues[2])
            return
        }
        if (HR.matches(t)) {
            endAll()
            out.append("<hr>")
            return
        }
        if (t.startsWith(">")) {
            endPara(); endLists(); endTable()
            quote.add(t.removePrefix(">").trim())
            return
        }
        if (t.startsWith("|")) {
            endPara(); endLists(); endQuote()
            table.add(t)
            return
        }
        val item = LIST.matchEntire(line)
        if (item != null) {
            endPara(); endQuote(); endTable()
            val indent = item.groupValues[1].length
            val tag = if (item.groupValues[2][0].isDigit()) "ol" else "ul"
            var content = item.groupValues[3]
            val task = TASK.find(content)
            if (task != null) {
                content = (if (task.groupValues[1] == " ") "☐ " else "☑ ") + content.substring(task.range.last + 1)
            }
            listItem(indent, tag, content)
            return
        }
        if (lists.isNotEmpty() && line.startsWith(" ")) { // wrapped list-item text
            out.append(' ').append(inline(t))
            return
        }
        endLists(); endQuote(); endTable()
        if (para.isNotEmpty()) para.append(' ')
        para.append(t)
    }

    private fun heading(level: Int, raw: String) {
        val text = raw.replace(TRAIL_HASH, "").trim()
        if (level <= 2 && out.length > 1500) flush()
        if (level <= 2 && chapterTitle == null) {
            chapterTitle = text.replace(MARKUP, "").ifBlank { null }
        }
        out.append("<h$level>").append(inline(text)).append("</h$level>")
    }

    private fun listItem(indent: Int, tag: String, content: String) {
        while (lists.isNotEmpty() && indent < lists.last().first) {
            out.append("</li></").append(lists.last().second).append('>')
            lists.removeAt(lists.lastIndex)
        }
        if (lists.isNotEmpty() && indent == lists.last().first) {
            out.append("</li><li>")
        } else {
            out.append('<').append(tag).append("><li>")
            lists.add(indent to tag)
        }
        out.append(inline(content))
    }

    private fun endPara() {
        if (para.isEmpty()) return
        out.append("<p>").append(inline(para.toString().trim())).append("</p>")
        para.setLength(0)
        if (out.length > 60_000) flush()
    }

    private fun endLists() {
        while (lists.isNotEmpty()) {
            out.append("</li></").append(lists.last().second).append('>')
            lists.removeAt(lists.lastIndex)
        }
    }

    private fun endQuote() {
        if (quote.isEmpty()) return
        out.append("<blockquote>")
        val buf = StringBuilder()
        for (q in quote) {
            if (q.isEmpty()) {
                if (buf.isNotEmpty()) { out.append("<p>").append(inline(buf.toString())).append("</p>"); buf.setLength(0) }
            } else {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(q)
            }
        }
        if (buf.isNotEmpty()) out.append("<p>").append(inline(buf.toString())).append("</p>")
        out.append("</blockquote>")
        quote.clear()
    }

    private fun endTable() {
        if (table.isEmpty()) return
        val rows = table
            .filterNot { it.contains('-') && TABLE_SEP.matches(it) }
            .map { row -> row.trim().trim('|').split('|').map { it.trim() } }
        out.append("<table>")
        rows.forEachIndexed { i, cells ->
            val tag = if (i == 0) "th" else "td"
            out.append("<tr>")
            cells.forEach { out.append('<').append(tag).append('>').append(inline(it)).append("</").append(tag).append('>') }
            out.append("</tr>")
        }
        out.append("</table>")
        table.clear()
    }

    private fun endAll() {
        endPara(); endLists(); endQuote(); endTable()
    }

    private fun flush() {
        if (out.isNotEmpty()) {
            titles.add(chapterTitle ?: "Part ${titles.size + 1}")
            bodies.add(out.toString())
        }
        out.setLength(0)
        chapterTitle = null
    }
}