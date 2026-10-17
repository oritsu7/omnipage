package com.ansh.omnipage.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {

    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    suspend fun getPosition(bookId: Long): ReadingPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: ReadingPositionEntity)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeMarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insertMark(mark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteMark(id: Long)
}
