package com.ansh.omnipage.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders WHERE treeUri = :uri LIMIT 1")
    suspend fun byTreeUri(uri: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: Long): FolderEntity?

    @Query("SELECT id FROM folders")
    suspend fun allIds(): List<Long>

    @Query(
        "SELECT folders.*, (SELECT COUNT(*) FROM books WHERE books.folderId = folders.id " +
            "AND books.trashed = 0 AND books.missing = 0) AS bookCount " +
            "FROM folders ORDER BY displayName COLLATE NOCASE"
    )
    fun observeWithCount(): Flow<List<FolderWithCount>>

    @Query("UPDATE folders SET lastScanAt = :time WHERE id = :id")
    suspend fun setScanned(id: Long, time: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
