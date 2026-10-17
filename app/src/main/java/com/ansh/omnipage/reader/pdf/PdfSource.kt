package com.ansh.omnipage.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 A PDF opened with the platform [PdfRenderer].
 PdfRenderer is not thread-safe and only allows one open page at a time, so every call that touches
 it goes through [lock]. Rendered pages are kept in a size-bounded LRU cache.
*/
class PdfSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val tempFile: File?,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    private val lock = Mutex()

    @Volatile
    private var closed = false

    // height / width per page; 0 = not measured yet. */
    private val ratios = FloatArray(pageCount.coerceAtLeast(1))

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun knownRatio(index: Int): Float = ratios.getOrElse(index) { 0f }

    suspend fun ratioOf(index: Int): Float {
        val known = knownRatio(index)
        if (known > 0f) return known
        return withContext(Dispatchers.IO) {
            lock.withLock {
                ensureOpen()
                renderer.openPage(index.coerceIn(0, pageCount - 1)).use { page ->
                    val r = page.height.toFloat() / page.width.coerceAtLeast(1)
                    if (index in ratios.indices) ratios[index] = r
                    r
                }
            }
        }
    }

    // Renders [index] at [widthPx] wide on a white background. Results are cached per (page, width). 
    suspend fun render(index: Int, widthPx: Int): Bitmap {
        val w = widthPx.coerceIn(64, 4096)
        val key = "$index@$w"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                ensureOpen()
                cache.get(key) ?: renderer.openPage(index.coerceIn(0, pageCount - 1)).use { page ->
                    val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 9000)
                    if (index in ratios.indices) ratios[index] = h.toFloat() / w
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bitmap)
                    bitmap
                }
            }
        }
    }

    private fun ensureOpen() {
        if (closed) throw CancellationException("PDF closed")
    }

    override fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        // Wait for any in-flight render to finish before tearing the renderer down.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            lock.withLock {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
                tempFile?.delete()
            }
        }
    }

    companion object {
        private fun cacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 4).coerceIn(24L shl 20, 160L shl 20).toInt()

        /**
         Opens [uri]. PdfRenderer needs a seekable descriptor; if the provider gives a pipe
         (some cloud providers do) the file is copied to the cache first.
         A password-protected PDF surfaces as [SecurityException].
        */
        suspend fun open(context: Context, uri: Uri): PdfSource = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val direct = resolver.openFileDescriptor(uri, "r")
            if (direct != null) {
                try {
                    return@withContext PdfSource(direct, PdfRenderer(direct), null)
                } catch (e: SecurityException) {
                    runCatching { direct.close() }
                    throw e
                } catch (e: Exception) {
                    runCatching { direct.close() } // probably not seekable — fall through to the copy
                }
            }
            val temp = File.createTempFile("pdf", ".pdf", context.cacheDir)
            try {
                val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read this file.")
                input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }
                val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfSource(pfd, PdfRenderer(pfd), temp)
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    throw e
                }
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        }
    }
}
