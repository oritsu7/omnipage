package com.ansh.omnipage.data.reader

import com.ansh.omnipage.data.LibraryRepository
import com.ansh.omnipage.data.db.AnnotationWithBook
import com.ansh.omnipage.data.db.BookmarkEntity
import com.ansh.omnipage.data.db.OmniDatabase
import com.ansh.omnipage.data.db.ReadingPositionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Reading positions, bookmarks, highlights, quotes and notes. Progress is mirrored into the library (Reading Now / Have Read). 
class ReaderRepository(db: OmniDatabase, private val library: LibraryRepository) {
    private val dao = db.readerDao()

    // Outlives any screen so the last position is saved even while the reader is being torn down. 
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun position(bookId: Long): String? = dao.getPosition(bookId)?.locator

    suspend fun markOpened(bookId: Long) = library.markOpened(bookId)

    fun savePositionAsync(bookId: Long, locator: String, progress: Float) {
        scope.launch {
            dao.upsertPosition(ReadingPositionEntity(bookId, locator, System.currentTimeMillis()))
            library.setProgress(bookId, progress)
        }
    }

    fun observeMarks(bookId: Long): Flow<List<BookmarkEntity>> = dao.observeMarks(bookId)

    // Every highlight, quote and note across the whole library, newest first. 
    fun observeAnnotations(): Flow<List<AnnotationWithBook>> = dao.observeAnnotations()

    suspend fun addMark(
        bookId: Long,
        kind: String,
        locator: String,
        label: String,
        quote: String = "",
        note: String = "",
    ) {
        dao.insertMark(
            BookmarkEntity(
                bookId = bookId,
                kind = kind,
                locator = locator,
                label = label,
                quote = quote,
                note = note,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    suspend fun deleteMark(id: Long) = dao.deleteMark(id)
}