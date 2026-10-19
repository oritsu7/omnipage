package com.ansh.omnipage.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansh.omnipage.AppContainer
import com.ansh.omnipage.data.BookFormat
import com.ansh.omnipage.data.bookFormat
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.data.db.BookmarkEntity
import com.ansh.omnipage.data.reader.ReaderSettings
import com.ansh.omnipage.reader.pdf.PdfSource
import com.ansh.omnipage.reader.reflow.EpubBook
import com.ansh.omnipage.reader.reflow.MarkdownBook
import com.ansh.omnipage.reader.reflow.ReflowBook
import com.ansh.omnipage.reader.reflow.TxtBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.abs

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data class Error(val message: String) : ReaderUi
    data class Pdf(val source: PdfSource) : ReaderUi
    data class Reflow(val book: ReflowBook) : ReaderUi
}

const val KIND_BOOKMARK = "bookmark"
const val KIND_QUOTE = "quote"
const val KIND_HIGHLIGHT = "highlight"
const val KIND_NOTE = "note"

// True when two locators ("pdf:<page>" / "ch:<chapter>:<fraction>") point at (nearly) the same spot. 
fun sameSpot(a: String, b: String): Boolean {
    if (a == b) return true
    val pa = a.split(':')
    val pb = b.split(':')
    if (pa.size == 3 && pb.size == 3 && pa[0] == "ch" && pb[0] == "ch" && pa[1] == pb[1]) {
        val fa = pa[2].toFloatOrNull() ?: return false
        val fb = pb[2].toFloatOrNull() ?: return false
        return abs(fa - fb) < 0.02f
    }
    return false
}

// "hl:<chapter>:<start>:<end>:<fraction>" (highlights / notes) -> "ch:<chapter>:<fraction>". Others pass through. 
private fun normalizeLocator(locator: String): String {
    val p = locator.split(':')
    return if (p.size == 5 && p[0] == "hl") "ch:${p[1]}:${p[4]}" else locator
}

class ReaderViewModel(
    private val bookId: Long,
    private val container: AppContainer,
    private val appContext: Context,
    // Open here instead of at the saved position (used when jumping from the Quotes & Notes screen). 
    private val startLocator: String? = null,
) : ViewModel() {

    private val _ui = MutableStateFlow<ReaderUi>(ReaderUi.Loading)
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    // null until DataStore has delivered the first value (avoids flashing default colours). 
    val settings: StateFlow<ReaderSettings?> =
        container.readerSettings.settings.stateIn<ReaderSettings?>(viewModelScope, SharingStarted.Eagerly, null)

    val marks: StateFlow<List<BookmarkEntity>> =
        container.reader.observeMarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Last known position; survives rotation because the ViewModel does.
    var pdfPage = 0
        private set
    var chapter = 0
        private set
    var fraction = 0f
        private set

    private var moved = false
    private var pendingLocator: String? = null
    private var pendingProgress = 0f
    private var saveJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val entity = container.repository.observeBook(bookId).first()
                ?: throw IOException("This book is no longer in your library.")
            _book.value = entity
            val saved = startLocator?.let { normalizeLocator(it) } ?: container.reader.position(bookId)
            container.reader.markOpened(bookId)
            val uri = Uri.parse(entity.uri)

            when (val format = entity.bookFormat) {
                BookFormat.PDF -> {
                    val source = PdfSource.open(appContext, uri)
                    pdfPage = parsePdf(saved).coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))
                    _ui.value = ReaderUi.Pdf(source)
                }
                BookFormat.EPUB -> {
                    val book = withContext(Dispatchers.IO) { EpubBook.open(appContext, uri) }
                    restoreReflow(book, saved)
                    _ui.value = ReaderUi.Reflow(book)
                }
                BookFormat.TXT -> {
                    val book = withContext(Dispatchers.IO) {
                        TxtBook.open(appContext, uri, entity.title.ifBlank { entity.fileName })
                    }
                    restoreReflow(book, saved)
                    _ui.value = ReaderUi.Reflow(book)
                }
                BookFormat.MD -> {
                    val book = withContext(Dispatchers.IO) {
                        MarkdownBook.open(appContext, uri, entity.title.ifBlank { entity.fileName })
                    }
                    restoreReflow(book, saved)
                    _ui.value = ReaderUi.Reflow(book)
                }
                else -> _ui.value = ReaderUi.Error(
                    "${format?.label ?: "This"} files can't be opened yet. " +
                        "PDF, EPUB, TXT and Markdown work now; more formats arrive in a later phase."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _ui.value = ReaderUi.Error(friendly(e))
        }
    }

    private fun parsePdf(locator: String?): Int {
        val parts = locator?.split(':') ?: return 0
        return if (parts.size == 2 && parts[0] == "pdf") parts[1].toIntOrNull() ?: 0 else 0
    }

    private fun restoreReflow(book: ReflowBook, locator: String?) {
        val parts = locator?.split(':') ?: return
        if (parts.size == 3 && parts[0] == "ch") {
            chapter = (parts[1].toIntOrNull() ?: 0).coerceIn(0, book.spine.lastIndex)
            fraction = (parts[2].toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is SecurityException ->
            "This file couldn't be opened. A PDF may be password-protected (not supported yet), " +
                "or access to its folder may have been revoked."
        is OutOfMemoryError -> "This file is too large to open on this device."
        is IOException -> e.message?.takeIf { it.isNotBlank() }
            ?: "The file couldn't be read. It may have been moved or deleted."
        else -> "Couldn't open this book (${e.javaClass.simpleName})."
    }

    // ---- position ---------------------------------------------------------------------------

    fun onPdfPage(page: Int, pageCount: Int) {
        if (!moved && page == pdfPage) return // the initial report just echoes the restored position
        moved = true
        pdfPage = page
        queueSave("pdf:$page", (page + 1f) / pageCount.coerceAtLeast(1))
    }

    fun onReflowPosition(book: ReflowBook, chapter: Int, fraction: Float) {
        if (!moved && chapter == this.chapter && abs(fraction - this.fraction) < 0.01f) return
        moved = true
        this.chapter = chapter
        this.fraction = fraction
        queueSave("ch:$chapter:$fraction", book.progressOf(chapter, fraction))
    }

    private fun queueSave(locator: String, progress: Float) {
        pendingLocator = locator
        pendingProgress = progress
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(900)
            flush()
        }
    }

    // Writes the latest position now (called on pause and when the screen goes away). 
    fun flush() {
        val locator = pendingLocator ?: return
        pendingLocator = null
        container.reader.savePositionAsync(bookId, locator, pendingProgress)
    }

    // ---- bookmarks / highlights / quotes / notes / settings ------------------------------------

    fun toggleBookmark(locator: String, label: String) {
        viewModelScope.launch {
            val hits = marks.value.filter { it.kind == KIND_BOOKMARK && sameSpot(it.locator, locator) }
            if (hits.isNotEmpty()) hits.forEach { container.reader.deleteMark(it.id) }
            else container.reader.addMark(bookId, KIND_BOOKMARK, locator, label)
        }
    }

    fun addQuote(locator: String, label: String, text: String) {
        viewModelScope.launch { container.reader.addMark(bookId, KIND_QUOTE, locator, label, text) }
    }

    // [locator] is "hl:<chapter>:<start>:<end>:<fraction>" — the text range that is painted in the page. 
    fun addHighlight(locator: String, label: String, text: String) {
        viewModelScope.launch { container.reader.addMark(bookId, KIND_HIGHLIGHT, locator, label, text) }
    }

    // Reflow notes use an "hl:" locator (painted like a highlight); PDF page notes use "pdf:<page>" and no quote. 
    fun addNote(locator: String, label: String, quote: String, note: String) {
        viewModelScope.launch { container.reader.addMark(bookId, KIND_NOTE, locator, label, quote, note) }
    }

    fun updateNote(id: Long, note: String) {
        viewModelScope.launch { container.reader.updateNote(id, note) }
    }

    fun deleteMark(id: Long) {
        viewModelScope.launch { container.reader.deleteMark(id) }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { container.readerSettings.update(transform) }
    }

    override fun onCleared() {
        flush()
        when (val state = _ui.value) {
            is ReaderUi.Pdf -> state.source.close()
            is ReaderUi.Reflow -> state.book.close()
            else -> Unit
        }
    }
}