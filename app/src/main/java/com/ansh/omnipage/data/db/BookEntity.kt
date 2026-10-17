package com.ansh.omnipage.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["fingerprint"]),
        Index(value = ["folderId"]),
        Index(value = ["author"]),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val folderId: Long? = null,
    val docId: String = "",
    // Path below the storage root, e.g. "Books/Fantasy/x.epub" (best effort; opaque for picked files). 
    val relPath: String = "",
    val fileName: String,
    val title: String,
    val author: String = "",
    val series: String = "",
    val seriesIndex: Float = 0f,
    // [com.ansh.omnipage.data.BookFormat] name. 
    val format: String,
    val sizeBytes: Long = 0,
    val lastModified: Long = 0,
    // Cheap content fingerprint (size + first/last 64 KB) used for duplicate and moved-file detection. 
    val fingerprint: String = "",
    val coverPath: String? = null,
    val pageCount: Int = 0,
    val progress: Float = 0f,
    val lastOpenedAt: Long = 0,
    val addedAt: Long = 0,
    val isFavorite: Boolean = false,
    val isToRead: Boolean = false,
    val isFinished: Boolean = false,
    val trashed: Boolean = false,
    val trashedAt: Long = 0,
    // File vanished from its folder; row is kept (hidden) so progress survives a move. 
    val missing: Boolean = false,
    // User edited title/author/series — rescans must not overwrite them. 
    val metaEdited: Boolean = false,
)

@Fts4(contentEntity = BookEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "books_fts")
data class BookFts(
    val title: String,
    val author: String,
    val series: String,
    val fileName: String,
)
