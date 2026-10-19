package com.ansh.omnipage.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Where the reader left off in a book. [locator] is "pdf:<page>" or "ch:<chapter>:<fraction>". 
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: Long,
    val locator: String,
    val updatedAt: Long,
)

// A bookmark, highlight, quote or note ([kind] = "bookmark" | "highlight" | "quote" | "note").
// [quote] is the selected text; [note] is what the reader typed (notes only). 
@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: String,
    val locator: String,
    val label: String,
    val quote: String = "",
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val createdAt: Long,
)

// A mark together with the title of its book, for the cross-book Quotes & Notes screen. 
data class AnnotationWithBook(
    @Embedded val mark: BookmarkEntity,
    val bookTitle: String,
)