package com.ansh.omnipage.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ansh.omnipage.data.reader.ReaderSettings
import com.ansh.omnipage.reader.reflow.ReflowAssets
import com.ansh.omnipage.reader.reflow.ReflowBook
import com.ansh.omnipage.ui.theme.Accent
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val TAG = "OmniReader"

enum class SelectionAction { HIGHLIGHT, QUOTE }

// A saved highlight: a character range [start, end) of one chapter's text. [id] is the database id. 
data class ReflowHighlight(val id: Long, val chapter: Int, val start: Int, val end: Int)

// WebView that adds "Highlight" and "Quote" to the text-selection menu. 
internal class OmniWebView(context: Context) : WebView(context) {

    var onSelection: ((SelectionAction, Int, Int, String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val wrapped = if (callback == null) null else SelectionCallback(callback)
        return super.startActionMode(wrapped, type)
    }

    private inner class SelectionCallback(private val base: ActionMode.Callback) : ActionMode.Callback2() {

        // The WebView rebuilds its menu in onPrepareActionMode (every time the selection changes), so our
        // items have to be (re)added there as well, not just in onCreateActionMode.
        private fun addItems(menu: Menu?) {
            if (menu == null) return
            if (menu.findItem(HIGHLIGHT_ID) == null) {
                menu.add(Menu.NONE, HIGHLIGHT_ID, 0, "Highlight").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            if (menu.findItem(QUOTE_ID) == null) {
                menu.add(Menu.NONE, QUOTE_ID, 1, "Quote").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val created = base.onCreateActionMode(mode, menu)
            addItems(menu)
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            base.onPrepareActionMode(mode, menu)
            addItems(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val action = when (item?.itemId) {
                HIGHLIGHT_ID -> SelectionAction.HIGHLIGHT
                QUOTE_ID -> SelectionAction.QUOTE
                else -> null
            }
            if (action != null) {
                // Read the selection first, close the menu afterwards (closing clears the selection).
                evaluateJavascript("(function(){return JSON.stringify(window.omni?omni.selectionInfo():null);})()") { raw ->
                    runCatching {
                        val json = JSONTokener(raw).nextValue() as? String
                        if (json != null && json != "null") {
                            val o = JSONObject(json)
                            onSelection?.invoke(action, o.getInt("start"), o.getInt("end"), o.getString("text"))
                        }
                    }
                    evaluateJavascript("if(window.omni)omni.clearSelection()", null)
                    mode?.finish()
                }
                return true
            }
            return base.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode?) = base.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
            if (base is ActionMode.Callback2) base.onGetContentRect(mode, view, outRect)
            else super.onGetContentRect(mode, view, outRect)
        }
    }

    private companion object {
        const val HIGHLIGHT_ID = 0x4F51
        const val QUOTE_ID = 0x4F52
    }
}

// Lets the reader screen drive the WebView (chapter jumps, seeking, paging). 
class ReflowController {
    internal var web: OmniWebView? = null
    internal var loader: ((Int, Float, String?) -> Unit)? = null

    fun openChapter(index: Int, fraction: Float = 0f, anchor: String? = null) {
        loader?.invoke(index, fraction, anchor)
    }

    // +1 = forward a page/screen, -1 = back. Crossing a chapter edge loads the neighbouring chapter. 
    fun step(direction: Int) {
        web?.evaluateJavascript("if(window.omni)omni.step($direction)", null)
    }

    fun seek(fraction: Float) {
        web?.evaluateJavascript("if(window.omni)omni.seek(${fraction.coerceIn(0f, 1f)})", null)
    }
}

private class Pending(val fraction: Float, val anchor: String?)

private class ReflowSession {
    var chapter = 0
    var fraction = 0f
    var ready = false
    var pending: Pending? = null
    var applied: ReaderSettings? = null
    var lastError: String? = null
}

/**
 * Methods the page's script may call. Must be a public class (internal compiles to public) so the
 * WebView's reflection can see it. Everything is bounced to the main thread.
*/
internal class ReflowBridge(
    private val web: View,
    private val ready: () -> Unit,
    private val position: (Float, Boolean, Boolean) -> Unit,
    private val edge: (Int) -> Unit,
    private val tap: (Float) -> Unit,
    private val highlightTap: (Long) -> Unit,
) {
    @JavascriptInterface
    fun onReady() { web.post { ready() } }

    @JavascriptInterface
    fun onPosition(fraction: Double, page: Double, pages: Double, atStart: Boolean, atEnd: Boolean) {
        web.post { position(fraction.toFloat(), atStart, atEnd) }
    }

    @JavascriptInterface
    fun onEdge(direction: Double) { web.post { edge(direction.toInt()) } }

    @JavascriptInterface
    fun onTap(x: Double, y: Double) { web.post { tap(x.toFloat()) } }

    @JavascriptInterface
    fun onHighlightTap(id: Double) { web.post { highlightTap(id.toLong()) } }
}

// [web] supplies the exact viewport size in CSS px, so page layout never depends on the browser guessing it. 
private fun configJson(s: ReaderSettings, web: View? = null): String = JSONObject().apply {
    put("bg", s.theme.css(s.theme.bg))
    put("fg", s.theme.css(s.theme.fg))
    put("link", s.theme.css(s.theme.link))
    put("fs", s.fontSize)
    put("lh", s.lineHeight.toDouble())
    put("mx", s.margin)
    put("ff", s.font.css)
    put("justify", s.justify)
    put("paged", s.paged)
    if (web != null && web.width > 0 && web.height > 0) {
        val density = web.resources.displayMetrics.density
        put("vw", (web.width / density).toDouble())
        put("vh", (web.height / density).toDouble())
    }
}.toString()

private fun highlightsJson(list: List<ReflowHighlight>): String = JSONArray().apply {
    list.forEach { put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end)) }
}.toString()

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReflowReader(
    book: ReflowBook,
    controller: ReflowController,
    settings: ReaderSettings,
    startChapter: Int,
    startFraction: Float,
    onPosition: (chapter: Int, fraction: Float, atStart: Boolean, atEnd: Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    highlights: List<ReflowHighlight>,
    onSelection: (chapter: Int, fraction: Float, action: SelectionAction, start: Int, end: Int, text: String) -> Unit,
    onHighlightTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestSettings by rememberUpdatedState(settings)
    val latestOnPosition by rememberUpdatedState(onPosition)
    val latestToggle by rememberUpdatedState(onToggleChrome)
    val latestSelection by rememberUpdatedState(onSelection)
    val latestHighlightTap by rememberUpdatedState(onHighlightTap)
    val session = remember(book) { ReflowSession() }

    // Compose-visible loading state. The WebView is never hidden with alpha=0 (that stops it drawing, and a
    // WebView that never draws never gets animation frames); a cover is drawn over it instead.
    var ready by remember(book) { mutableStateOf(false) }
    var problem by remember(book) { mutableStateOf<String?>(null) }
    var loadToken by remember(book) { mutableStateOf(0) }

    LaunchedEffect(loadToken) {
        if (loadToken == 0) return@LaunchedEffect
        delay(6000)
        if (!ready) problem = session.lastError ?: "This chapter didn't finish loading."
    }

    LaunchedEffect(ready, highlights) {
        if (ready) {
            val mine = highlights.filter { it.chapter == session.chapter }
            controller.web?.evaluateJavascript("if(window.omni)omni.setHighlights(${highlightsJson(mine)})", null)
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.web = null
            controller.loader = null
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val web = OmniWebView(ctx)
                web.setBackgroundColor(Color(latestSettings.theme.bg).toArgb())
                web.isVerticalScrollBarEnabled = false
                web.isHorizontalScrollBarEnabled = false
                web.overScrollMode = View.OVER_SCROLL_NEVER
                with(web.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    textZoom = 100
                    mediaPlaybackRequiresUserGesture = true
                    // No text auto-sizing / wide-viewport tricks: the reader controls layout itself.
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    // The reader paints its own themes; stop the platform from "force darkening" on top.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        forceDark = WebSettings.FORCE_DARK_OFF
                    }
                }

                fun load(index: Int, fraction: Float, anchor: String?) {
                    val i = index.coerceIn(0, book.spine.lastIndex)
                    // Already showing this chapter: just move within it instead of reloading.
                    if (i == session.chapter && session.ready) {
                        if (anchor != null) {
                            web.evaluateJavascript("if(window.omni)omni.anchor(${JSONObject.quote(anchor)})", null)
                        } else {
                            web.evaluateJavascript("if(window.omni)omni.seek(${fraction.coerceIn(0f, 1f)})", null)
                        }
                        return
                    }
                    session.chapter = i
                    session.fraction = fraction
                    session.ready = false
                    session.lastError = null
                    session.pending = Pending(fraction.coerceIn(0f, 1f), anchor)
                    ready = false
                    problem = null
                    loadToken += 1
                    val url = ReflowAssets.chapterUrl(book, i)
                    Log.d(TAG, "loading chapter $i: $url")
                    web.loadUrl(url)
                }

                fun handleLink(uri: Uri) {
                    if (uri.host == ReflowAssets.HOST) {
                        val idx = book.indexOfPath(uri.path.orEmpty().removePrefix("/"))
                        val fragment = uri.fragment
                        if (idx < 0) return
                        if (idx == session.chapter) {
                            if (!fragment.isNullOrEmpty()) {
                                web.evaluateJavascript("if(window.omni)omni.anchor(${JSONObject.quote(fragment)})", null)
                            }
                        } else {
                            load(idx, 0f, fragment)
                        }
                        return
                    }
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https" || scheme == "mailto") {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }

                web.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                        if (message != null) {
                            Log.w(TAG, "js ${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                session.lastError = "Reader script error: ${message.message()}"
                            }
                        }
                        return true
                    }
                }

                web.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val uri = request?.url ?: return null
                        return ReflowAssets.intercept(ctx.applicationContext, book, uri)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        request?.url?.let { handleLink(it) }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "page finished: $url")
                        // Ignore the WebView's own about:blank etc.; only our chapters count.
                        if (url == null || !url.startsWith("https://${ReflowAssets.HOST}/")) return
                        val p = session.pending ?: return
                        session.pending = null
                        val s = latestSettings
                        session.applied = s
                        val anchor = p.anchor?.let { JSONObject.quote(it) } ?: "null"
                        web.evaluateJavascript(
                            "if(window.omni){omni.start(${configJson(s, web)},${p.fraction},$anchor);}" +
                                "else{console.error('reader script did not load');OmniBridge.onReady();}",
                            null,
                        )
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.w(TAG, "load error ${error?.errorCode} ${error?.description} for ${request?.url}")
                        if (request?.isForMainFrame == true) {
                            session.lastError = "Couldn't load the chapter (${error?.description})."
                        }
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        // Returning true keeps the app alive; "Try again" recovers.
                        session.ready = false
                        session.lastError = "The page renderer stopped unexpectedly."
                        ready = false
                        problem = session.lastError
                        return true
                    }
                }

                web.addJavascriptInterface(
                    ReflowBridge(
                        web = web,
                        ready = {
                            session.ready = true
                            ready = true
                            problem = null
                            val s = latestSettings
                            if (session.applied != s) { // settings changed while the chapter was loading
                                session.applied = s
                                web.evaluateJavascript("if(window.omni)omni.configure(${configJson(s, web)})", null)
                            }
                        },
                        position = { fraction, atStart, atEnd ->
                            if (session.ready) {
                                session.fraction = fraction
                                latestOnPosition(session.chapter, fraction, atStart, atEnd)
                            }
                        },
                        edge = { direction ->
                            if (session.ready) {
                                if (direction > 0 && session.chapter < book.spine.lastIndex) load(session.chapter + 1, 0f, null)
                                else if (direction < 0 && session.chapter > 0) load(session.chapter - 1, 1f, null)
                            }
                        },
                        tap = { x ->
                            val s = latestSettings
                            when {
                                s.tapZones && x < 0.28f -> controller.step(-1)
                                s.tapZones && x > 0.72f -> controller.step(1)
                                else -> latestToggle()
                            }
                        },
                        highlightTap = { id -> latestHighlightTap(id) },
                    ),
                    "OmniBridge",
                )

                web.onSelection = { action, start, end, text ->
                    latestSelection(session.chapter, session.fraction, action, start, end, text)
                }

                // Tell the page its real size whenever Android changes it (first layout, rotation, split screen).
                web.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        val d = v.resources.displayMetrics.density
                        web.evaluateJavascript(
                            "if(window.omni)omni.resize(${(right - left) / d},${(bottom - top) / d})",
                            null,
                        )
                    }
                }

                controller.web = web
                controller.loader = { i, f, a -> load(i, f, a) }
                session.chapter = -1 // force the first load()
                load(startChapter, startFraction, null)
                web
            },
            update = { web ->
                web.setBackgroundColor(Color(settings.theme.bg).toArgb())
                if (session.ready && session.applied != settings) {
                    session.applied = settings
                    web.evaluateJavascript("if(window.omni)omni.configure(${configJson(settings, web)})", null)
                }
            },
            onRelease = { web ->
                web.stopLoading()
                web.removeJavascriptInterface("OmniBridge")
                web.destroy()
            },
        )

        if (!ready) {
            Box(
                Modifier.fillMaxSize().background(Color(settings.theme.bg)),
                contentAlignment = Alignment.Center,
            ) {
                val message = problem
                if (message == null) {
                    CircularProgressIndicator(color = Accent)
                } else {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(message, color = Color(settings.theme.fg), textAlign = TextAlign.Center)
                        Button(
                            onClick = {
                                session.ready = false
                                session.pending = null
                                controller.openChapter(session.chapter.coerceAtLeast(0), session.fraction)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1300)),
                        ) { Text("Try again") }
                    }
                }
            }
        }
    }
}
