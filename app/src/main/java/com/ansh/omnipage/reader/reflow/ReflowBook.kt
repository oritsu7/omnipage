package com.ansh.omnipage.reader.reflow

import java.io.Closeable
import java.io.InputStream

// One readable chapter file. [weight] (≈ size) is used to turn "chapter + fraction" into whole-book progress. 
data class SpineItem(val path: String, val weight: Long)

data class TocItem(val title: String, val spineIndex: Int, val anchor: String?, val depth: Int)

class BookResource(val mime: String, val stream: InputStream)

// A book that is shown as HTML in a WebView (EPUB, plain text, later MOBI/FB2/DOCX…). 
abstract class ReflowBook : Closeable {
    abstract val title: String?
    abstract val spine: List<SpineItem>
    abstract val toc: List<TocItem>

    abstract fun chapterHtml(index: Int): String
    abstract fun resource(path: String): BookResource?

    private val totalWeight: Long by lazy { spine.sumOf { it.weight }.coerceAtLeast(1L) }

    fun indexOfPath(path: String): Int {
        val exact = spine.indexOfFirst { it.path == path }
        return if (exact >= 0) exact else spine.indexOfFirst { it.path.equals(path, ignoreCase = true) }
    }

    // Whole-book progress (0..1) for a position inside chapter [index]. 
    fun progressOf(index: Int, fraction: Float): Float {
        if (spine.isEmpty()) return 0f
        val i = index.coerceIn(0, spine.lastIndex)
        var before = 0L
        for (k in 0 until i) before += spine[k].weight
        val position = before.toDouble() + fraction.coerceIn(0f, 1f).toDouble() * spine[i].weight
        return (position / totalWeight).toFloat().coerceIn(0f, 1f)
    }

    // Inverse of [progressOf]: which chapter and fraction does a book-wide progress value land on. 
    fun locate(progress: Float): Pair<Int, Float> {
        val target = progress.coerceIn(0f, 1f).toDouble() * totalWeight
        var acc = 0L
        for ((i, item) in spine.withIndex()) {
            if (target <= acc + item.weight || i == spine.lastIndex) {
                val f = ((target - acc) / item.weight.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
                return i to f
            }
            acc += item.weight
        }
        return 0 to 0f
    }

    fun tocTitleAt(spineIndex: Int): String? = toc.lastOrNull { it.spineIndex <= spineIndex }?.title
}
