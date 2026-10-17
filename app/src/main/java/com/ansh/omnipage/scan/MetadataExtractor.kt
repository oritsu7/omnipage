package com.ansh.omnipage.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Xml
import com.ansh.omnipage.data.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import kotlin.coroutines.cancellation.CancellationException

data class ExtractedMeta(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Float = 0f,
    val pageCount: Int = 0,
    val cover: Bitmap? = null,
)

/**
 Pulls title / author / series / page count / cover out of a document.
 Phase 1 covers PDF, EPUB, CBZ and DOCX; every other format falls back to its file name
 (MOBI/AZW3, FB2, DJVU, … get real metadata when their engines land in later phases).
 Never throws: a broken file simply yields empty metadata.
*/
class MetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri, format: BookFormat): ExtractedMeta = withContext(Dispatchers.IO) {
        try {
            when (format) {
                BookFormat.PDF -> pdf(uri)
                BookFormat.EPUB -> epub(uri)
                BookFormat.CBZ -> cbz(uri)
                BookFormat.DOCX -> docx(uri)
                else -> ExtractedMeta()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExtractedMeta()
        } catch (e: OutOfMemoryError) {
            ExtractedMeta()
        }
    }

    // PDF

    private fun pdf(uri: Uri): ExtractedMeta {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return ExtractedMeta()
        return pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) {
                    ExtractedMeta()
                } else {
                    val bitmap = renderer.openPage(0).use { page ->
                        val width = COVER_WIDTH
                        val height = (width * page.height.toFloat() / page.width).toInt().coerceIn(100, 2400)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(Color.WHITE)
                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                    ExtractedMeta(pageCount = renderer.pageCount, cover = bitmap)
                }
            }
        }
    }

    // EPUB

    private class OpfInfo(
        val title: String?,
        val authors: List<String>,
        val series: String?,
        val seriesIndex: Float,
        val coverHref: String?,
    )

    private fun epub(uri: Uri): ExtractedMeta = ZipOpener.open(context, uri).use { handle ->
        val zip = handle.zip
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return@use ExtractedMeta()
        val opfPath = zip.getInputStream(containerEntry).use { parseRootFile(it) } ?: return@use ExtractedMeta()
        val opfEntry = zip.getEntry(opfPath) ?: return@use ExtractedMeta()
        val opf = zip.getInputStream(opfEntry).use { parseOpf(it) }

        var cover: Bitmap? = null
        val coverHref = opf.coverHref
        if (coverHref != null) {
            val path = resolveZipPath(opfPath, coverHref)
            val entry = zip.getEntry(path)
                ?: zip.entries().toList().firstOrNull { it.name.equals(path, ignoreCase = true) }
            if (entry != null) {
                cover = decodeScaled(zip.getInputStream(entry).use { it.readBytesCapped(MAX_COVER_BYTES) })
            }
        }

        ExtractedMeta(
            title = opf.title,
            author = opf.authors.take(3).joinToString(", ").ifEmpty { null },
            series = opf.series,
            seriesIndex = opf.seriesIndex,
            cover = cover,
        )
    }

    private fun parseRootFile(stream: InputStream): String? {
        val p = Xml.newPullParser()
        p.setInput(stream, null)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name.substringAfter(':') == "rootfile") {
                return p.getAttributeValue(null, "full-path")
            }
            ev = p.next()
        }
        return null
    }

    private fun parseOpf(stream: InputStream): OpfInfo {
        class Item(val href: String, val type: String, val props: String)

        val p = Xml.newPullParser()
        p.setInput(stream, null)

        var title: String? = null
        val authors = ArrayList<String>()
        var calibreSeries: String? = null
        var calibreIndex: Float? = null
        val collections = ArrayList<Pair<String?, String>>() // (id, name)
        val positions = HashMap<String, Float?>()
        var coverId: String? = null
        val manifest = LinkedHashMap<String, Item>()
        var inMetadata = false

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name.substringAfter(':')) {
                    "metadata" -> inMetadata = true
                    "title" -> if (inMetadata && title == null) {
                        title = p.nextText().trim().ifEmpty { null }
                    }
                    "creator" -> if (inMetadata) {
                        val name = p.nextText().trim()
                        if (name.isNotEmpty()) authors.add(name)
                    }
                    "meta" -> {
                        val name = p.getAttributeValue(null, "name")
                        val content = p.getAttributeValue(null, "content")
                        val property = p.getAttributeValue(null, "property")
                        val id = p.getAttributeValue(null, "id")
                        val refines = p.getAttributeValue(null, "refines")
                        when {
                            name == "cover" && content != null -> coverId = content
                            name == "calibre:series" -> calibreSeries = content
                            name == "calibre:series_index" -> calibreIndex = content?.toFloatOrNull()
                            property == "belongs-to-collection" -> collections.add(id to p.nextText().trim())
                            property == "group-position" && refines != null ->
                                positions[refines.removePrefix("#")] = p.nextText().trim().toFloatOrNull()
                        }
                    }
                    "item" -> {
                        val id = p.getAttributeValue(null, "id")
                        val href = p.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = Item(
                                href,
                                p.getAttributeValue(null, "media-type") ?: "",
                                p.getAttributeValue(null, "properties") ?: "",
                            )
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name.substringAfter(':') == "metadata") {
                inMetadata = false
            }
            ev = p.next()
        }

        val coverHref = manifest.values.firstOrNull { "cover-image" in it.props.split(' ') }?.href
            ?: coverId?.let { manifest[it]?.href }
            ?: manifest.entries
                .firstOrNull { it.key.contains("cover", ignoreCase = true) && it.value.type.startsWith("image/") }
                ?.value?.href

        val firstCollection = collections.firstOrNull { it.second.isNotEmpty() }
        val series = calibreSeries?.takeIf { it.isNotBlank() } ?: firstCollection?.second
        val seriesIndex = calibreIndex
            ?: firstCollection?.first?.let { positions[it] }
            ?: 0f

        return OpfInfo(title, authors, series, seriesIndex, coverHref)
    }

    // CBZ

    private fun cbz(uri: Uri): ExtractedMeta = ZipOpener.open(context, uri).use { handle ->
        val images = handle.zip.entries().toList()
            .filter { entry ->
                !entry.isDirectory &&
                    !entry.name.startsWith("__MACOSX") &&
                    !entry.name.substringAfterLast('/').startsWith(".") &&
                    isImageName(entry.name)
            }
            .sortedWith(Comparator<ZipEntry> { a, b -> naturalCompare(a.name, b.name) })

        if (images.isEmpty()) {
            ExtractedMeta()
        } else {
            val bytes = handle.zip.getInputStream(images.first()).use { it.readBytesCapped(MAX_COVER_BYTES) }
            ExtractedMeta(pageCount = images.size, cover = decodeScaled(bytes))
        }
    }

    // DOCX

    private fun docx(uri: Uri): ExtractedMeta = ZipOpener.open(context, uri).use { handle ->
        val entry = handle.zip.getEntry("docProps/core.xml") ?: return@use ExtractedMeta()
        var title: String? = null
        var author: String? = null
        handle.zip.getInputStream(entry).use { stream ->
            val p = Xml.newPullParser()
            p.setInput(stream, null)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name.substringAfter(':')) {
                        "title" -> title = p.nextText().trim().ifEmpty { null }
                        "creator" -> author = p.nextText().trim().ifEmpty { null }
                    }
                }
                ev = p.next()
            }
        }
        ExtractedMeta(title = title, author = author)
    }

    // helpers

    private fun isImageName(name: String): Boolean =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp" -> true
            else -> false
        }

    private fun decodeScaled(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun InputStream.readBytesCapped(max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun resolveZipPath(baseFile: String, href: String): String {
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

    private companion object {
        const val COVER_WIDTH = 600
        const val MAX_COVER_BYTES = 15 * 1024 * 1024
    }
}

// "page2" sorts before "page10".
internal fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ei = i
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j
            while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0')
            val nb = b.substring(j, ej).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = ei
            j = ej
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}
