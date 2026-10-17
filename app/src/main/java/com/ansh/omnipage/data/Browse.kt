package com.ansh.omnipage.data

import androidx.sqlite.db.SimpleSQLiteQuery

enum class ViewMode {
    LIST, GRID;

    companion object {
        fun from(name: String?): ViewMode = entries.firstOrNull { it.name == name } ?: LIST
    }
}

enum class SortOrder(val label: String) {
    RECENTLY_ADDED("Recently added"),
    RECENTLY_OPENED("Recently opened"),
    TITLE("Title"),
    AUTHOR("Author"),
    SIZE("Size");

    companion object {
        fun from(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: RECENTLY_ADDED
    }
}

// The drawer destinations, in drawer order. 
enum class Section(val title: String) {
    READING_NOW("Reading Now"),
    ALL("Books & documents"),
    FAVORITES("Favorites"),
    TO_READ("To Read"),
    HAVE_READ("Have Read"),
    AUTHORS("Authors"),
    SERIES("Series"),
    COLLECTIONS("Collections"),
    FORMATS("Formats"),
    FOLDERS("Folders"),
    DOWNLOADS("Downloads"),
    TRASH("Trash");

    // Sections that first show a list of groups and only then the books of one group. 
    val isGrouped: Boolean get() = this == AUTHORS || this == SERIES || this == FORMATS || this == FOLDERS
}

sealed interface GroupFilter {
    val label: String

    data class Author(val name: String) : GroupFilter {
        override val label: String get() = name
    }

    data class Series(val name: String) : GroupFilter {
        override val label: String get() = name
    }

    data class Format(val format: BookFormat) : GroupFilter {
        override val label: String get() = format.label
    }

    data class Folder(val id: Long, val name: String) : GroupFilter {
        override val label: String get() = name
    }
}

data class LibraryQuery(
    val section: Section,
    val search: String,
    val group: GroupFilter?,
    val sort: SortOrder,
)

// Turns free text into an FTS4 prefix query ("tolk rin" -> "tolk* rin*"); null when nothing searchable. 
internal fun ftsMatch(raw: String): String? {
    val tokens = raw.split(Regex("\\s+"))
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { "$it*" }
}

internal fun buildBooksQuery(q: LibraryQuery): SimpleSQLiteQuery {
    val where = mutableListOf("missing = 0")
    val args = mutableListOf<Any>()

    where.add(if (q.section == Section.TRASH) "trashed = 1" else "trashed = 0")
    when (q.section) {
        Section.READING_NOW -> where.add("lastOpenedAt > 0 AND isFinished = 0")
        Section.FAVORITES -> where.add("isFavorite = 1")
        Section.TO_READ -> where.add("isToRead = 1")
        Section.HAVE_READ -> where.add("isFinished = 1")
        Section.DOWNLOADS -> where.add("(relPath LIKE 'Download/%' OR relPath LIKE 'Downloads/%')")
        else -> Unit
    }

    when (val g = q.group) {
        is GroupFilter.Author -> { where.add("author = ? COLLATE NOCASE"); args.add(g.name) }
        is GroupFilter.Series -> { where.add("series = ? COLLATE NOCASE"); args.add(g.name) }
        is GroupFilter.Format -> { where.add("format = ?"); args.add(g.format.name) }
        is GroupFilter.Folder -> { where.add("folderId = ?"); args.add(g.id) }
        null -> Unit
    }

    ftsMatch(q.search)?.let { match ->
        where.add("id IN (SELECT rowid FROM books_fts WHERE books_fts MATCH ?)")
        args.add(match)
    }

    val order = when {
        q.section == Section.READING_NOW -> "lastOpenedAt DESC"
        q.section == Section.TRASH -> "trashedAt DESC"
        q.group is GroupFilter.Series -> "seriesIndex ASC, title COLLATE NOCASE ASC"
        else -> when (q.sort) {
            SortOrder.TITLE -> "title COLLATE NOCASE ASC"
            SortOrder.AUTHOR ->
                "CASE WHEN author = '' THEN 1 ELSE 0 END, author COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
            SortOrder.RECENTLY_ADDED -> "addedAt DESC"
            SortOrder.RECENTLY_OPENED -> "lastOpenedAt DESC, addedAt DESC"
            SortOrder.SIZE -> "sizeBytes DESC"
        }
    }

    return SimpleSQLiteQuery(
        "SELECT * FROM books WHERE ${where.joinToString(" AND ")} ORDER BY $order",
        args.toTypedArray(),
    )
}
