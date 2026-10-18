package com.ansh.omnipage.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A user-made shelf. Names are unique (the repository also checks case-insensitively).
@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

// Membership: one row per (book, collection). No foreign keys; orphans are purged by the repository.
@Entity(
    tableName = "book_collections",
    primaryKeys = ["bookId", "collectionId"],
    indices = [Index(value = ["collectionId"])],
)
data class BookCollectionRef(
    val bookId: Long,
    val collectionId: Long,
)

data class CollectionWithCount(
    @Embedded val collection: CollectionEntity,
    val bookCount: Int,
)