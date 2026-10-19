package com.ansh.omnipage.data

import com.ansh.omnipage.data.db.BookEntity

enum class BookFormat(
    val label: String,
    val mime: String,
    val extensions: List<String>,
    val fixedLayout: Boolean,
) {
    PDF("PDF", "application/pdf", listOf("pdf"), true),
    EPUB("EPUB", "application/epub+zip", listOf("epub"), false),
    MOBI("MOBI", "application/x-mobipocket-ebook", listOf("mobi", "prc"), false),
    AZW3("AZW3", "application/vnd.amazon.ebook", listOf("azw3", "azw"), false),
    FB2("FB2", "application/x-fictionbook+xml", listOf("fb2"), false),
    DOCX("DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", listOf("docx"), false),
    DOC("DOC", "application/msword", listOf("doc"), false),
    RTF("RTF", "application/rtf", listOf("rtf"), false),
    ODT("ODT", "application/vnd.oasis.opendocument.text", listOf("odt"), false),
    TXT("TXT", "text/plain", listOf("txt"), false),
    MD("MD", "text/markdown", listOf("md", "markdown"), false),
    CBZ("CBZ", "application/vnd.comicbook+zip", listOf("cbz"), true),
    DJVU("DJVU", "image/vnd.djvu", listOf("djvu", "djv"), true),
    CHM("CHM", "application/vnd.ms-htmlhelp", listOf("chm"), false);

    companion object {
        private val byExtension: Map<String, BookFormat> =
            entries.flatMap { f -> f.extensions.map { it to f } }.toMap()

        fun fromFileName(name: String): BookFormat? =
            byExtension[name.substringAfterLast('.', "").lowercase()]

        fun fromName(name: String?): BookFormat? = entries.firstOrNull { it.name == name }
    }
}

val BookEntity.bookFormat: BookFormat? get() = BookFormat.fromName(format)
