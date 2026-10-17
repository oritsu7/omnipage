package com.ansh.omnipage.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ansh.omnipage.OmniPageApp
import com.ansh.omnipage.data.db.BookmarkEntity
import com.ansh.omnipage.reader.reflow.ReflowBook
import com.ansh.omnipage.ui.theme.Accent
import com.ansh.omnipage.ui.theme.Night
import com.ansh.omnipage.ui.theme.Panel
import com.ansh.omnipage.ui.theme.Teal
import com.ansh.omnipage.ui.theme.TextHigh
import com.ansh.omnipage.ui.theme.TextMuted
import kotlin.math.roundToInt

private const val PANEL_NONE = 0
private const val PANEL_CONTENTS = 1
private const val PANEL_SETTINGS = 2

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun reflowLabel(book: ReflowBook, chapter: Int, fraction: Float): String {
    val name = book.tocTitleAt(chapter) ?: "Section ${chapter + 1}"
    return "$name · ${(book.progressOf(chapter, fraction) * 100).roundToInt()}%"
}

// "hl:<chapter>:<start>:<end>:<fraction>" -> highlight to paint, or null if malformed. 
private fun parseHighlight(mark: BookmarkEntity): ReflowHighlight? {
    val p = mark.locator.split(':')
    if (p.size != 5 || p[0] != "hl") return null
    val chapter = p[1].toIntOrNull() ?: return null
    val start = p[2].toIntOrNull() ?: return null
    val end = p[3].toIntOrNull() ?: return null
    return ReflowHighlight(mark.id, chapter, start, end)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as OmniPageApp).container }
    val vm: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = viewModelFactory {
            initializer { ReaderViewModel(bookId, container, context.applicationContext) }
        },
    )

    val ui by vm.ui.collectAsStateWithLifecycle()
    val settingsOrNull by vm.settings.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val bookEntity by vm.book.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.flush() }

    val settings = settingsOrNull
    if (settings == null) {
        Box(Modifier.fillMaxSize().background(Night))
        return
    }
    val state = ui

    // window: brightness + keep awake
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, settings.brightness) {
        val window = activity?.window
        if (window != null) {
            val lp = window.attributes
            lp.screenBrightness =
                if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.brightness
            window.attributes = lp
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    DisposableEffect(activity, settings.keepScreenOn) {
        if (settings.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // screen state
    val pdfController = remember { PdfController() }
    val reflowController = remember { ReflowController() }
    var chrome by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(PANEL_NONE) }
    var pdfPage by remember { mutableIntStateOf(vm.pdfPage) }
    var chapter by remember { mutableIntStateOf(vm.chapter) }
    var fraction by remember { mutableFloatStateOf(vm.fraction) }
    var atStart by remember { mutableStateOf(true) }
    var atEnd by remember { mutableStateOf(false) }
    var editingMark by remember { mutableStateOf<BookmarkEntity?>(null) }
    val highlights = remember(marks) { marks.filter { it.kind == KIND_HIGHLIGHT }.mapNotNull { parseHighlight(it) } }

    // The book finishes loading after first composition; pick up the restored position.
    LaunchedEffect(state) {
        pdfPage = vm.pdfPage
        chapter = vm.chapter
        fraction = vm.fraction
    }

    val pdf = state as? ReaderUi.Pdf
    val reflow = state as? ReaderUi.Reflow

    val locator = when {
        pdf != null -> "pdf:$pdfPage"
        reflow != null -> "ch:$chapter:$fraction"
        else -> ""
    }
    val locatorLabel = when {
        pdf != null -> "Page ${pdfPage + 1}"
        reflow != null -> reflowLabel(reflow.book, chapter, fraction)
        else -> ""
    }
    val bookmarked = locator.isNotEmpty() && marks.any { it.kind == KIND_BOOKMARK && sameSpot(it.locator, locator) }

    fun openLocator(target: String) {
        val parts = target.split(':')
        when {
            parts[0] == "pdf" -> parts.getOrNull(1)?.toIntOrNull()?.let { pdfController.goToPage(it) }
            parts[0] == "ch" && parts.size == 3 ->
                reflowController.openChapter(parts[1].toIntOrNull() ?: 0, parts[2].toFloatOrNull() ?: 0f)
            parts[0] == "hl" && parts.size == 5 ->
                reflowController.openChapter(parts[1].toIntOrNull() ?: 0, parts[4].toFloatOrNull() ?: 0f)
        }
    }

    // layout
    Box(Modifier.fillMaxSize().background(Color(settings.theme.bg))) {

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            when (state) {
                ReaderUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)

                is ReaderUi.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(state.message, color = Color(settings.theme.fg), textAlign = TextAlign.Center)
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1300)),
                    ) { Text("Back to library") }
                }

                is ReaderUi.Pdf -> PdfReader(
                    source = state.source,
                    settings = settings,
                    startPage = vm.pdfPage,
                    controller = pdfController,
                    onPage = { page ->
                        pdfPage = page
                        vm.onPdfPage(page, state.source.pageCount)
                    },
                    onToggleChrome = { chrome = !chrome },
                )

                is ReaderUi.Reflow -> key(state.book) {
                    ReflowReader(
                        book = state.book,
                        controller = reflowController,
                        settings = settings,
                        startChapter = vm.chapter,
                        startFraction = vm.fraction,
                        onPosition = { ch, f, start, end ->
                            chapter = ch
                            fraction = f
                            atStart = start
                            atEnd = end
                            vm.onReflowPosition(state.book, ch, f)
                        },
                        onToggleChrome = { chrome = !chrome },
                        highlights = highlights,
                        onSelection = { ch, f, action, start, end, text ->
                            val label = reflowLabel(state.book, ch, f)
                            when (action) {
                                SelectionAction.HIGHLIGHT -> {
                                    vm.addHighlight("hl:$ch:$start:$end:$f", label, text)
                                    Toast.makeText(context, "Highlighted", Toast.LENGTH_SHORT).show()
                                }
                                SelectionAction.QUOTE -> {
                                    vm.addQuote("ch:$ch:$f", label, text)
                                    Toast.makeText(context, "Quote saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onHighlightTap = { id -> editingMark = marks.firstOrNull { it.id == id } },
                    )
                }
            }

            // Continuous-scroll books can't be swiped into the next chapter, so offer a button at the edges.
            if (reflow != null && !settings.paged) {
                AnimatedVisibility(
                    visible = atEnd && chapter < reflow.book.spine.lastIndex,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) { EdgePill("Next chapter ›") { reflowController.openChapter(chapter + 1, 0f) } }
                AnimatedVisibility(
                    visible = atStart && chapter > 0,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) { EdgePill("‹ Previous chapter") { reflowController.openChapter(chapter - 1, 1f) } }
            }
        }

        // Teal status-bar strip and a dark nav-bar strip keep system icons legible on any page colour.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Teal)
                .align(Alignment.TopCenter)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(Night)
                .align(Alignment.BottomCenter)
        )

        // top bar
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = { Text(bookEntity?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (locator.isNotEmpty()) {
                        IconButton(onClick = { vm.toggleBookmark(locator, locatorLabel) }) {
                            Icon(
                                if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (bookmarked) Accent else Color.White,
                            )
                        }
                        IconButton(onClick = { panel = PANEL_CONTENTS }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Contents and bookmarks")
                        }
                        IconButton(onClick = { panel = PANEL_SETTINGS }) {
                            Icon(Icons.Filled.Tune, "Reading settings")
                        }
                    }
                },
            )
        }

        // bottom bar
        AnimatedVisibility(
            visible = chrome && locator.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(color = Panel, contentColor = TextHigh) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (pdf != null) {
                        val count = pdf.source.pageCount
                        Text(
                            "Page ${pdfPage + 1} of $count",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        if (count > 1) {
                            var drag by remember { mutableStateOf<Float?>(null) }
                            Slider(
                                value = drag ?: pdfPage.toFloat(),
                                onValueChange = { drag = it },
                                onValueChangeFinished = {
                                    drag?.let { pdfController.goToPage(it.roundToInt()) }
                                    drag = null
                                },
                                valueRange = 0f..(count - 1).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                            )
                        }
                    } else if (reflow != null) {
                        val book = reflow.book
                        val progress = book.progressOf(chapter, fraction)
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { reflowController.openChapter(chapter - 1, 0f) }, enabled = chapter > 0) {
                                Icon(Icons.Filled.SkipPrevious, "Previous chapter")
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${book.tocTitleAt(chapter) ?: "Section ${chapter + 1}"} · ${((drag ?: progress) * 100).roundToInt()}%",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                                Slider(
                                    value = drag ?: progress,
                                    onValueChange = { drag = it },
                                    onValueChangeFinished = {
                                        drag?.let { p ->
                                            val (target, f) = book.locate(p)
                                            if (target == chapter) reflowController.seek(f)
                                            else reflowController.openChapter(target, f)
                                        }
                                        drag = null
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                                )
                            }
                            IconButton(
                                onClick = { reflowController.openChapter(chapter + 1, 0f) },
                                enabled = chapter < book.spine.lastIndex,
                            ) { Icon(Icons.Filled.SkipNext, "Next chapter") }
                        }
                    }
                }
            }
        }
    }

    editingMark?.let { mark ->
        AlertDialog(
            onDismissRequest = { editingMark = null },
            title = { Text("Highlight") },
            text = { Text(mark.quote, maxLines = 8, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMark(mark.id)
                    editingMark = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { editingMark = null }) { Text("Close") } },
        )
    }

    // sheets
    if (panel == PANEL_CONTENTS && (pdf != null || reflow != null)) {
        ContentsSheet(
            isPdf = pdf != null,
            pageCount = pdf?.source?.pageCount ?: 0,
            currentPage = pdfPage,
            toc = reflow?.book?.toc.orEmpty(),
            currentChapter = chapter,
            marks = marks,
            onGoToPage = { page ->
                pdfController.goToPage(page)
                panel = PANEL_NONE
            },
            onOpenToc = { item ->
                reflowController.openChapter(item.spineIndex, 0f, item.anchor)
                panel = PANEL_NONE
            },
            onOpenMark = { mark ->
                openLocator(mark.locator)
                panel = PANEL_NONE
            },
            onDeleteMark = { vm.deleteMark(it.id) },
            onDismiss = { panel = PANEL_NONE },
        )
    }
    if (panel == PANEL_SETTINGS) {
        SettingsSheet(
            settings = settings,
            isPdf = pdf != null,
            onChange = { transform -> vm.updateSettings(transform) },
            onDismiss = { panel = PANEL_NONE },
        )
    }
}

@Composable
private fun EdgePill(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Teal,
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}
