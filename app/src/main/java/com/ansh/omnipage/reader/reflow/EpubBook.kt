package com.ansh.omnipage.reader.reflow

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.ansh.omnipage.scan.ZipHandle
import com.ansh.omnipage.scan.ZipOpener
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry

// An EPUB read straight out of its zip: OPF spine for reading order, NCX / nav document for the contents. 
internal class EpubBook private constructor(
    private val handle: ZipHandle,
    private val files: Map<String, ZipEntry>,
    private val lowerFiles: Map<String, ZipEntry>,
    override val title: String?,
    override val spine: List<SpineItem>,
    override val toc: List<TocItem>,
) : ReflowBook() {

    private fun entry(path: String): ZipEntry? = files[path] ?: lowerFiles[path.lowercase()]

    override fun chapterHtml(index: Int): String {
        val e = entry(spine[index.coerceIn(0, spine.lastIndex)].path) ?: return ""
        return decode(handle.zip.getInputStream(e).use { it.readBytes() })
    }

    override fun resource(path: String): BookResource? {
        val e = entry(path) ?: return null
        return BookResource(mimeFor(path), handle.zip.getInputStream(e))
    }

    override fun close() = handle.close()

    private class ManifestItem(val id: String, val href: String, val type: String, val props: String)

    private class Opf(
        val title: String?,
        val manifest: Map<String, ManifestItem>,
        val spineIds: List<String>,
        val tocId: String?,
    )

    private class RawToc(var title: String, var src: String, val depth: Int)

    companion object {

        fun open(context: Context, uri: Uri): EpubBook {
            val handle = ZipOpener.open(context, uri)
            try {
                val zip = handle.zip
                val files = HashMap<String, ZipEntry>()
                val lower = HashMap<String, ZipEntry>()
                for (e in zip.entries().toList()) {
                    if (!e.isDirectory) {
                        files[e.name] = e
                        lower.putIfAbsent(e.name.lowercase(), e)
                    }
                }

                fun read(path: String): ByteArray? {
                    val e = files[path] ?: lower[path.lowercase()] ?: return null
                    return zip.getInputStream(e).use { it.readBytes() }
                }

                val container = read("META-INF/container.xml") ?: throw IOException("This isn't a valid EPUB.")
                val opfPath = parseRootFile(container) ?: throw IOException("This EPUB has no package document.")
                val opfBytes = read(opfPath) ?: throw IOException("This EPUB's package document is missing.")
                val opf = parseOpf(opfBytes)

                // ---- spine -------------------------------------------------------------
                val spine = ArrayList<SpineItem>()
                for (id in opf.spineIds) {
                    val item = opf.manifest[id] ?: continue
                    val ext = item.href.substringBefore('#').substringAfterLast('.', "").lowercase()
                    val isDoc = item.type.contains("html", ignoreCase = true) || ext in setOf("xhtml", "html", "htm")
                    if (!isDoc) continue
                    val path = resolvePath(opfPath, item.href)
                    val e = files[path] ?: lower[path.lowercase()] ?: continue
                    spine.add(SpineItem(e.name, e.size.coerceAtLeast(1L)))
                }
                if (spine.isEmpty()) throw IOException("This EPUB has no readable chapters.")

                val indexByPath = HashMap<String, Int>()
                val indexByLower = HashMap<String, Int>()
                spine.forEachIndexed { i, s ->
                    indexByPath.putIfAbsent(s.path, i)
                    indexByLower.putIfAbsent(s.path.lowercase(), i)
                }

                // contents
                val raw = ArrayList<Pair<String, RawToc>>() // (document path the hrefs are relative to, entry)
                runCatching {
                    val ncx = opf.tocId?.let { opf.manifest[it] }
                        ?: opf.manifest.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
                    val nav = opf.manifest.values.firstOrNull { "nav" in it.props.split(' ') }
                    if (ncx != null) {
                        val ncxPath = resolvePath(opfPath, ncx.href)
                        read(ncxPath)?.let { bytes -> parseNcx(bytes).forEach { raw.add(ncxPath to it) } }
                    }
                    if (raw.isEmpty() && nav != null) {
                        val navPath = resolvePath(opfPath, nav.href)
                        read(navPath)?.let { bytes -> parseNav(bytes).forEach { raw.add(navPath to it) } }
                    }
                }

                val toc = ArrayList<TocItem>()
                for ((docPath, t) in raw) {
                    val pathPart = t.src.substringBefore('#')
                    val fragment = t.src.substringAfter('#', "").ifEmpty { null }
                    if (pathPart.isEmpty()) continue
                    val resolved = resolvePath(docPath, pathPart)
                    val idx = indexByPath[resolved] ?: indexByLower[resolved.lowercase()] ?: continue
                    val label = t.title.trim().ifEmpty { "Section ${toc.size + 1}" }
                    toc.add(TocItem(label, idx, fragment, t.depth))
                }
                if (toc.isEmpty()) {
                    spine.forEachIndexed { i, _ -> toc.add(TocItem("Section ${i + 1}", i, null, 0)) }
                }

                return EpubBook(handle, files, lower, opf.title, spine, toc)
            } catch (e: Throwable) {
                handle.close()
                throw e
            }
        }

        // XML helpers

        private fun newParser(bytes: ByteArray): XmlPullParser {
            val p = Xml.newPullParser()
            runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            p.setInput(ByteArrayInputStream(bytes), null)
            return p
        }

        private fun local(name: String?): String = name?.substringAfter(':').orEmpty()

        private fun parseRootFile(bytes: ByteArray): String? {
            val p = newParser(bytes)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && local(p.name) == "rootfile") {
                    return p.getAttributeValue(null, "full-path")
                }
                ev = p.next()
            }
            return null
        }

        private fun parseOpf(bytes: ByteArray): Opf {
            val p = newParser(bytes)
            var title: String? = null
            var tocId: String? = null
            var inMetadata = false
            val manifest = LinkedHashMap<String, ManifestItem>()
            val spineIds = ArrayList<String>()

            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (local(p.name)) {
                        "metadata" -> inMetadata = true
                        "title" -> if (inMetadata && title == null) title = p.nextText().trim().ifEmpty { null }
                        "item" -> {
                            val id = p.getAttributeValue(null, "id")
                            val href = p.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                manifest[id] = ManifestItem(
                                    id,
                                    href,
                                    p.getAttributeValue(null, "media-type") ?: "",
                                    p.getAttributeValue(null, "properties") ?: "",
                                )
                            }
                        }
                        "spine" -> tocId = p.getAttributeValue(null, "toc")
                        "itemref" -> p.getAttributeValue(null, "idref")?.let { spineIds.add(it) }
                    }
                } else if (ev == XmlPullParser.END_TAG && local(p.name) == "metadata") {
                    inMetadata = false
                }
                ev = p.next()
            }
            return Opf(title, manifest, spineIds, tocId)
        }

        // EPUB 2 table of contents. Entries are returned in document (pre-)order with their nesting depth. 
        private fun parseNcx(bytes: ByteArray): List<RawToc> {
            val p = newParser(bytes)
            val out = ArrayList<RawToc>()
            val stack = ArrayList<RawToc>()
            var inLabel = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (local(p.name)) {
                        "navPoint" -> {
                            val t = RawToc("", "", stack.size)
                            out.add(t)
                            stack.add(t)
                        }
                        "navLabel" -> inLabel = true
                        "text" -> if (inLabel && stack.isNotEmpty()) {
                            val text = p.nextText()
                            val top = stack.last()
                            if (top.title.isEmpty()) top.title = text
                        }
                        "content" -> if (stack.isNotEmpty()) {
                            val top = stack.last()
                            if (top.src.isEmpty()) top.src = p.getAttributeValue(null, "src").orEmpty()
                        }
                    }
                    XmlPullParser.END_TAG -> when (local(p.name)) {
                        "navLabel" -> inLabel = false
                        "navPoint" -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    }
                }
                ev = p.next()
            }
            return out.filter { it.src.isNotEmpty() }
        }

        // EPUB 3 navigation document: the <nav epub:type="toc"> list. 
        private fun parseNav(bytes: ByteArray): List<RawToc> {
            val p = newParser(bytes)
            val out = ArrayList<RawToc>()
            var inToc = false
            var navDepth = 0
            var listDepth = 0
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (local(p.name)) {
                        "nav" -> {
                            navDepth++
                            val type = p.getAttributeValue(null, "epub:type") ?: p.getAttributeValue(null, "type") ?: ""
                            if (!inToc && "toc" in type.split(' ')) inToc = true
                        }
                        "ol" -> if (inToc) listDepth++
                        "a" -> if (inToc) {
                            val href = p.getAttributeValue(null, "href").orEmpty()
                            val label = innerText(p)
                            if (href.isNotEmpty()) out.add(RawToc(label.trim(), href, (listDepth - 1).coerceAtLeast(0)))
                            ev = p.next() // innerText stops on the </a> end tag
                            continue
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (local(p.name)) {
                        "ol" -> if (inToc && listDepth > 0) listDepth--
                        "nav" -> {
                            navDepth--
                            if (inToc && navDepth == 0) return out
                        }
                    }
                }
                ev = p.next()
            }
            return out
        }

        // Text of the current element (including nested tags); leaves the parser on its end tag. 
        private fun innerText(p: XmlPullParser): String {
            val sb = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (p.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.TEXT -> sb.append(p.text)
                    XmlPullParser.END_DOCUMENT -> return sb.toString()
                }
            }
            return sb.toString().replace(Regex("\\s+"), " ")
        }

        // Resolves [href] against the zip path of the file that contains it. 
        fun resolvePath(baseFile: String, href: String): String {
            val clean = Uri.decode(href.substringBefore('#').substringBefore('?'))
            val baseDir = baseFile.substringBeforeLast('/', "")
            val joined = if (baseDir.isEmpty()) clean else "$baseDir/$clean"
            val parts = ArrayList<String>()
            for (segment in joined.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(segment)
                }
            }
            return parts.joinToString("/")
        }

        private val ENCODING = Regex("encoding=[\"']([A-Za-z0-9_\\-]+)[\"']")

        private fun decode(bytes: ByteArray): String {
            var charset: Charset = Charsets.UTF_8
            val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
            ENCODING.find(head)?.let { m -> runCatching { charset = Charset.forName(m.groupValues[1]) } }
            return String(bytes, charset).removePrefix("\uFEFF")
        }

        private fun mimeFor(path: String): String =
            when (path.substringAfterLast('.', "").lowercase()) {
                "css" -> "text/css"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "xhtml", "html", "htm" -> "text/html"
                "js" -> "application/javascript"
                "ncx", "xml" -> "application/xml"
                else -> "application/octet-stream"
            }
    }
}
