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

    @Query(
        "SELECT bookmarks.*, books.title AS bookTitle FROM bookmarks " +
            "JOIN books ON books.id = bookmarks.bookId " +
            "WHERE bookmarks.kind IN ('highlight', 'quote', 'note') " +
            "ORDER BY bookmarks.createdAt DESC"
    )
    fun observeAnnotations(): Flow<List<AnnotationWithBook>>

    @Insert
    suspend fun insertMark(mark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteMark(id: Long)

    // Left behind when books are removed from the library. 
    @Query("DELETE FROM reading_positions WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun purgeOrphanPositions()

    @Query("DELETE FROM bookmarks WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun purgeOrphanMarks()
}