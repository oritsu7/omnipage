package com.ansh.omnipage.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders", indices = [Index(value = ["treeUri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScanAt: Long = 0,
)

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val bookCount: Int,
)

data class GroupCount(
    val name: String,
    val count: Int,
)
