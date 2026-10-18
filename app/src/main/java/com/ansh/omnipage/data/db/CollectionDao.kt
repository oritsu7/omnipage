package com.ansh.omnipage.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query(
        "SELECT collections.*, (SELECT COUNT(*) FROM book_collections bc " +
            "JOIN books b ON b.id = bc.bookId " +
            "WHERE bc.collectionId = collections.id AND b.trashed = 0 AND b.missing = 0) AS bookCount " +
            "FROM collections ORDER BY name COLLATE NOCASE"
    )
    fun observeWithCount(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT collectionId FROM book_collections WHERE bookId = :bookId")
    fun observeIdsForBook(bookId: Long): Flow<List<Long>>

    @Query("SELECT * FROM collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM book_collections WHERE collectionId = :collectionId")
    suspend fun clearMembers(collectionId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRef(ref: BookCollectionRef)

    @Query("DELETE FROM book_collections WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeRef(bookId: Long, collectionId: Long)

    // Rows left behind when books were deleted (e.g. a folder was removed). 
    @Query("DELETE FROM book_collections WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun purgeOrphans()
}