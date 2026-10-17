package com.ansh.omnipage.reader.reflow

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import com.ansh.omnipage.R
import java.io.ByteArrayInputStream

/**
 Serves a [ReflowBook] to a WebView under the virtual origin https://epub.local/.
 Chapters are rewritten on the fly (scripts stripped, reader CSS/JS injected); everything that is not
 part of the book or the reader is blocked, so book content can never reach the network.
*/
object ReflowAssets {
    const val HOST = "epub.local"
    private const val ORIGIN = "https://epub.local/"

    private val SCRIPT_SELF = Regex("<script\\b[^>]*/>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_BLOCK =
        Regex("<script\\b[^>]*>.*?</script\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** XHTML allows <div/>; the HTML parser would treat it as an unclosed tag and swallow the page. */
    private val SELF_CLOSING = Regex(
        "<(a|div|span|p|title|h[1-6]|i|b|em|strong|section|li|ul|ol|td|tr|table|style|body|head|iframe|label|" +
            "button|nav|aside|header|footer|article|figure|figcaption|blockquote|small|sup|sub|u|cite|code|pre|" +
            "dl|dt|dd)(\\s[^>]*?)?\\s*/>",
        RegexOption.IGNORE_CASE,
    )
    private val XML_PROLOG = Regex("^\\s*<\\?xml[^>]*\\?>")
    private val DOCTYPE = Regex("<!DOCTYPE[^>\\[]*(\\[[^\\]]*\\])?\\s*>", RegexOption.IGNORE_CASE)
    private val HEAD_CLOSE = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
    private val BODY_OPEN = Regex("<body\\b", RegexOption.IGNORE_CASE)

    private const val HEAD_INJECT =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<link rel=\"stylesheet\" href=\"https://epub.local/__omni/reader.css\">" +
            "<script defer src=\"https://epub.local/__omni/reader.js\"></script>"

    fun chapterUrl(book: ReflowBook, index: Int): String =
        ORIGIN + Uri.encode(book.spine[index.coerceIn(0, book.spine.lastIndex)].path, "/")

    fun intercept(context: Context, book: ReflowBook, uri: Uri): WebResourceResponse {
        if (uri.host != HOST) return status(403)
        val path = uri.path.orEmpty().removePrefix("/")
        return try {
            when {
                path == "__omni/reader.css" -> raw(context, R.raw.omni_reader_css, "text/css")
                path == "__omni/reader.js" -> raw(context, R.raw.omni_reader_js, "application/javascript")
                path.startsWith("__omni/fonts/") -> font(context, path.removePrefix("__omni/fonts/"))
                else -> {
                    val idx = book.indexOfPath(path)
                    if (idx >= 0) {
                        val html = prepare(book.chapterHtml(idx))
                        WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
                    } else {
                        book.resource(path)?.let { WebResourceResponse(it.mime, null, it.stream) } ?: status(404)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("OmniReader", "intercept failed for $path", e)
            status(404)
        }
    }

    /**
     Makes an XHTML chapter safe and consistent for the WebView:
     scripts removed, self-closing tags fixed, reader CSS/JS injected, and a plain `<!DOCTYPE html>` so the
     page is rendered in standards mode (an XML prolog before the doctype would put it in quirks mode and
     break the page measurements the reader relies on).
    */
    fun prepare(raw: String): String {
        var s = raw.removePrefix("\uFEFF")
        s = XML_PROLOG.replace(s, "")
        s = DOCTYPE.replaceFirst(s, "")
        s = SCRIPT_SELF.replace(s, "")
        s = SCRIPT_BLOCK.replace(s, "")
        s = SELF_CLOSING.replace(s) { m -> "<${m.groupValues[1]}${m.groupValues[2]}></${m.groupValues[1]}>" }

        val headClose = HEAD_CLOSE.find(s)
        val body = BODY_OPEN.find(s)
        val html = when {
            headClose != null -> {
                val at = headClose.range.first
                s.substring(0, at) + HEAD_INJECT + s.substring(at)
            }
            body != null -> {
                val at = body.range.first
                s.substring(0, at) + "<head>" + HEAD_INJECT + "</head>" + s.substring(at)
            }
            else -> "<html><head>$HEAD_INJECT</head><body>$s</body></html>"
        }
        return "<!DOCTYPE html>" + html
    }

    private fun raw(context: Context, id: Int, mime: String) =
        WebResourceResponse(mime, "utf-8", context.resources.openRawResource(id))

    private fun font(context: Context, name: String): WebResourceResponse {
        val id = when (name) {
            "literata.ttf" -> R.font.literata_var
            "source_serif.ttf" -> R.font.source_serif_var
            "inter.ttf" -> R.font.inter_var
            else -> return status(404)
        }
        return WebResourceResponse("font/ttf", null, context.resources.openRawResource(id))
    }

    private fun status(code: Int) = WebResourceResponse(
        "text/plain", "utf-8", code, if (code == 403) "Forbidden" else "Not Found",
        emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )
}
