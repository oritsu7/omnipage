package com.ansh.omnipage.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // reads

    @RawQuery(observedEntities = [BookEntity::class, BookCollectionRef::class])
    fun observeRaw(query: SupportSQLiteQuery): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE fingerprint = :fp AND sizeBytes = :size AND fingerprint != ''")
    suspend fun getByFingerprint(fp: String, size: Long): List<BookEntity>

    @Query("SELECT COUNT(*) FROM books WHERE coverPath = :path")
    suspend fun coverRefs(path: String): Int

    @Query(
        "SELECT author AS name, COUNT(*) AS count FROM books " +
            "WHERE trashed = 0 AND missing = 0 AND author != '' " +
            "GROUP BY author COLLATE NOCASE ORDER BY author COLLATE NOCASE"
    )
    fun observeAuthors(): Flow<List<GroupCount>>

    @Query(
        "SELECT series AS name, COUNT(*) AS count FROM books " +
            "WHERE trashed = 0 AND missing = 0 AND series != '' " +
            "GROUP BY series COLLATE NOCASE ORDER BY series COLLATE NOCASE"
    )
    fun observeSeries(): Flow<List<GroupCount>>

    @Query(
        "SELECT format AS name, COUNT(*) AS count FROM books " +
            "WHERE trashed = 0 AND missing = 0 GROUP BY format ORDER BY count DESC"
    )
    fun observeFormats(): Flow<List<GroupCount>>

    // scan writes

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Query(
        "UPDATE books SET uri = :uri, docId = :docId, relPath = :relPath, fileName = :fileName, " +
            "folderId = :folderId, lastModified = :modified, missing = 0 WHERE id = :id"
    )
    suspend fun relocate(
        id: Long, uri: String, docId: String, relPath: String, fileName: String, folderId: Long?, modified: Long,
    )

    @Query(
        "UPDATE books SET sizeBytes = :size, lastModified = :modified, fingerprint = :fp, " +
            "pageCount = :pages, coverPath = :cover, missing = 0, " +
            "title = CASE WHEN metaEdited = 1 THEN title ELSE :title END, " +
            "author = CASE WHEN metaEdited = 1 THEN author ELSE :author END, " +
            "series = CASE WHEN metaEdited = 1 THEN series ELSE :series END, " +
            "seriesIndex = CASE WHEN metaEdited = 1 THEN seriesIndex ELSE :seriesIndex END " +
            "WHERE id = :id"
    )
    suspend fun applyRescan(
        id: Long, size: Long, modified: Long, fp: String, pages: Int, cover: String?,
        title: String, author: String, series: String, seriesIndex: Float,
    )

    @Query("UPDATE books SET missing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    // user writes

    @Query("UPDATE books SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE books SET isToRead = NOT isToRead WHERE id = :id")
    suspend fun toggleToRead(id: Long)

    @Query(
        "UPDATE books SET isFinished = NOT isFinished, " +
            "progress = CASE WHEN isFinished = 0 THEN 1.0 WHEN progress >= 1.0 THEN 0.0 ELSE progress END " +
            "WHERE id = :id"
    )
    suspend fun toggleFinished(id: Long)

    @Query("UPDATE books SET trashed = 1, trashedAt = :now WHERE id = :id")
    suspend fun trash(id: Long, now: Long)

    @Query("UPDATE books SET trashed = 0, trashedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE books SET trashed = 0, trashedAt = 0 WHERE trashed = 1")
    suspend fun restoreAll()

    @Query("UPDATE books SET title = :title, author = :author, series = :series, metaEdited = 1 WHERE id = :id")
    suspend fun editMeta(id: Long, title: String, author: String, series: String)

    @Query("UPDATE books SET lastOpenedAt = :now WHERE id = :id")
    suspend fun markOpened(id: Long, now: Long)

    @Query(
        "UPDATE books SET progress = :progress, " +
            "isFinished = CASE WHEN :progress >= 0.999 THEN 1 ELSE 0 END WHERE id = :id"
    )
    suspend fun setProgress(id: Long, progress: Float)

    @Query("DELETE FROM books WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)
}
