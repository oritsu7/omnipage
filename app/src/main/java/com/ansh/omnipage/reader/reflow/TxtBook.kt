package com.ansh.omnipage.reader.reflow

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

// Plain text, split into chapter-sized chunks and wrapped in minimal HTML.
class TxtBook private constructor(
    override val title: String?,
    private val titles: List<String>,
    private val bodies: List<String>,
) : ReflowBook() {

    override val spine: List<SpineItem> =
        bodies.indices.map { SpineItem("txt/$it.html", bodies[it].length.toLong().coerceAtLeast(1L)) }

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
        private const val MAX_BYTES = 40 * 1024 * 1024
        private const val CHUNK_CHARS = 20_000
        private val HEADING = Regex("^(chapter|part|book|prologue|epilogue|section)\\b.{0,70}$", RegexOption.IGNORE_CASE)

        fun open(context: Context, uri: Uri, fallbackTitle: String?): TxtBook {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot read this file.")
            if (bytes.size > MAX_BYTES) throw IOException("This text file is too large to open.")

            val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')

            // Books with blank lines between paragraphs are usually hard-wrapped: join the wrapped lines.
            val paragraphs = if (text.contains("\n\n")) {
                text.split(Regex("\n{2,}")).map { it.replace('\n', ' ').replace(Regex("\\s+"), " ").trim() }
            } else {
                text.split('\n').map { it.trim() }
            }.filter { it.isNotEmpty() }

            if (paragraphs.isEmpty()) throw IOException("This text file is empty.")

            val titles = ArrayList<String>()
            val bodies = ArrayList<String>()
            val sb = StringBuilder()
            var currentTitle: String? = null
            var chars = 0

            fun flush() {
                if (sb.isNotEmpty()) {
                    titles.add(currentTitle ?: "Part ${titles.size + 1}")
                    bodies.add(sb.toString())
                    sb.setLength(0)
                    currentTitle = null
                    chars = 0
                }
            }

            for (p in paragraphs) {
                val isHeading = p.length <= 80 && HEADING.matches(p)
                if (isHeading && chars > 3000) flush()
                else if (!isHeading && chars > CHUNK_CHARS) flush()
                if (isHeading) {
                    if (currentTitle == null) currentTitle = p
                    sb.append("<h2>").append(escape(p)).append("</h2>")
                } else {
                    sb.append("<p>").append(escape(p)).append("</p>")
                }
                chars += p.length
            }
            flush()

            return TxtBook(fallbackTitle, titles, bodies)
        }

        private fun decode(bytes: ByteArray): String {
            if (bytes.size >= 2) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return String(bytes, Charsets.UTF_16)
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }
        }

        private fun escape(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
