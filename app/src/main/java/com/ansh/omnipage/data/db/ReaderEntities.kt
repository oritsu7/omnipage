package com.ansh.omnipage.data.db

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

// A bookmark or a saved quote ([kind] = "bookmark" | "quote"). 
@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: String,
    val locator: String,
    val label: String,
    val quote: String = "",
    val createdAt: Long,
)
