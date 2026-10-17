package com.ansh.omnipage.data.reader

/**
Declaration order = order in the picker (light themes first).
Names of the original five are kept so saved preferences keep working.
*/
enum class ReaderTheme(
    val label: String,
    val bg: Long,
    val fg: Long,
    val link: Long,
    val dark: Boolean,
) {
    PAPER("Paper", 0xFFFFFFFF, 0xFF1B1B1B, 0xFF0B5FA5, false),
    MIST("Mist", 0xFFDDE3EA, 0xFF1F2933, 0xFF1F6FB0, false),
    SEPIA("Sepia", 0xFFF1E4C3, 0xFF4A3520, 0xFF8A4B14, false),
    SLATE("Graphite", 0xFF35393E, 0xFFD6D9DC, 0xFF86BDEB, true),
    NIGHT("Midnight", 0xFF0E1F3A, 0xFFC7D5EC, 0xFF6FB1F2, true),
    BLACK("AMOLED", 0xFF000000, 0xFFA9A9A9, 0xFF5AA9E6, true),
    MOCHA("Mocha", 0xFF2A1D15, 0xFFE3CDB1, 0xFFE09A52, true),
    FOREST("Forest", 0xFF10241A, 0xFFC4DCC9, 0xFF6CC48B, true);

    fun css(color: Long): String = "#%06X".format(color and 0xFFFFFFL)
}

enum class ReaderFont(val label: String, val css: String) {
    LITERATA("Literata", "'OmniLiterata', Georgia, serif"),
    SOURCE_SERIF("Source Serif", "'OmniSourceSerif', Georgia, serif"),
    INTER("Inter", "'OmniInter', system-ui, sans-serif"),
    SERIF("Serif", "Georgia, 'Times New Roman', serif"),
    SANS("Sans", "Roboto, 'Helvetica Neue', Arial, sans-serif"),
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.NIGHT,
    val font: ReaderFont = ReaderFont.LITERATA,
    // CSS px. 
    val fontSize: Int = 19,
    val lineHeight: Float = 1.6f,
    // Horizontal page margin in CSS px. 
    val margin: Int = 22,
    val justify: Boolean = false,
    // Reflowable books: true = page turning, false = continuous scroll. 
    val paged: Boolean = true,
    // 0..1, or -1 to follow the system. 
    val brightness: Float = -1f,
    val keepScreenOn: Boolean = true,
    val tapZones: Boolean = true,
    // PDF: true = one page at a time, false = continuous scroll. 
    val pdfPaged: Boolean = false,
    // PDF: repaint pages in the reader theme's colours (off = original white pages). 
    val pdfInvert: Boolean = true,
)
