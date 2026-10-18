package com.ansh.omnipage.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ansh.omnipage.data.db.BookEntity
import com.ansh.omnipage.data.db.FolderEntity
import com.ansh.omnipage.data.db.FolderWithCount
import com.ansh.omnipage.data.db.GroupCount
import com.ansh.omnipage.data.db.OmniDatabase
import com.ansh.omnipage.data.db.BookCollectionRef
import com.ansh.omnipage.data.db.CollectionEntity
import com.ansh.omnipage.data.db.CollectionWithCount
import com.ansh.omnipage.scan.FoundDoc
import com.ansh.omnipage.scan.FolderScanner
import com.ansh.omnipage.scan.Fingerprint
import com.ansh.omnipage.scan.MetadataExtractor
import com.ansh.omnipage.scan.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ImportResult(val added: Int, val skipped: Int)

class LibraryRepository(
    private val context: Context,
    db: OmniDatabase,
    private val scanner: FolderScanner,
    private val extractor: MetadataExtractor,
    private val covers: CoverStore,
) {
    private val books = db.bookDao()
    private val folders = db.folderDao()
    private val collections = db.collectionDao()

    // One scan at a time: avoids two workers inserting the same file. 
    private val scanMutex = Mutex()

    // observation

    fun observeBooks(query: LibraryQuery): Flow<List<BookEntity>> =
        if (query.section.isGrouped && query.group == null) flowOf(emptyList())
        else books.observeRaw(buildBooksQuery(query))

    fun observeBook(id: Long): Flow<BookEntity?> = books.observeById(id)

    fun observeGroups(section: Section): Flow<List<GroupCount>> = when (section) {
        Section.AUTHORS -> books.observeAuthors()
        Section.SERIES -> books.observeSeries()
        Section.FORMATS -> books.observeFormats()
        else -> flowOf(emptyList())
    }

    fun observeFolders(): Flow<List<FolderWithCount>> = folders.observeWithCount()

    suspend fun folderIds(): List<Long> = folders.allIds()

    // folders

    suspend fun addFolder(tree: Uri): Long = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val key = tree.toString()
        val existing = folders.byTreeUri(key)
        if (existing != null) {
            existing.id
        } else {
            val name = DocumentFile.fromTreeUri(context, tree)?.name
                ?: Uri.decode(tree.lastPathSegment ?: "").substringAfterLast(':').substringAfterLast('/')
            val id = folders.insert(
                FolderEntity(
                    treeUri = key,
                    displayName = name.ifBlank { "Folder" },
                    addedAt = System.currentTimeMillis(),
                )
            )
            if (id > 0) id else folders.byTreeUri(key)?.id ?: -1L
        }
    }

    // Removes the folder and its books from the library. Files on storage are never touched. 
    suspend fun removeFolder(id: Long) {
        val folder = folders.byId(id) ?: return
        val owned = books.getByFolder(id)
        books.deleteByFolder(id)
        collections.purgeOrphans()
        folders.delete(id)
        owned.forEach { deleteCoverIfOrphan(it.coverPath) }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    // scanning

    suspend fun scanFolder(folderId: Long, onProgress: suspend (Int) -> Unit = {}) {
        scanMutex.withLock { doScan(folderId, onProgress) }
    }

    private suspend fun doScan(folderId: Long, onProgress: suspend (Int) -> Unit) {
        val folder = folders.byId(folderId) ?: return
        val result = scanner.enumerate(Uri.parse(folder.treeUri), onProgress)
        syncFolder(folderId, result, onProgress)
        if (result.complete) folders.setScanned(folderId, System.currentTimeMillis())
    }

    private suspend fun syncFolder(folderId: Long, result: ScanResult, onProgress: suspend (Int) -> Unit) {
        val existing = books.getByFolder(folderId)
        val byUri = existing.associateBy { it.uri }
        val liveUris = result.docs.mapTo(HashSet()) { it.uri.toString() }
        val seen = HashSet<Long>()

        result.docs.forEachIndexed { index, doc ->
            currentCoroutineContext().ensureActive()
            val prev = byUri[doc.uri.toString()]
            if (prev != null) {
                seen.add(prev.id)
                val unchanged = prev.sizeBytes == doc.size && prev.lastModified == doc.modified
                if (unchanged) {
                    if (prev.missing) books.setMissing(prev.id, false)
                } else {
                    refresh(prev, doc)
                }
            } else {
                addOrRevive(doc, folderId, liveUris)?.let { seen.add(it) }
            }
            if (index % 5 == 0) onProgress(index + 1)
        }

        // Only hide vanished books when the walk saw the whole tree; a half-read folder must not hide anything.
        if (result.complete) {
            existing.filter { it.id !in seen && !it.missing }.forEach { books.setMissing(it.id, true) }
        }
    }

    // ingest

    // Inserts a new book, revives/relocates a moved one, or returns null for a duplicate. 
    private suspend fun addOrRevive(doc: FoundDoc, folderId: Long?, liveUris: Set<String>): Long? {
        val format = BookFormat.fromFileName(doc.name) ?: return null
        val fingerprint = Fingerprint.compute(context.contentResolver, doc.uri, doc.size).orEmpty()

        if (fingerprint.isNotEmpty()) {
            val twins = books.getByFingerprint(fingerprint, doc.size)
            if (twins.isNotEmpty()) {
                val moved = twins.firstOrNull {
                    it.missing || (it.uri !in liveUris && !scanner.exists(Uri.parse(it.uri)))
                }
                if (moved != null) {
                    books.relocate(
                        moved.id, doc.uri.toString(), doc.docId, doc.relPath, doc.name, folderId, doc.modified,
                    )
                    return moved.id
                }
                return null // same bytes already in the library under another path
            }
        }

        val meta = extractor.extract(doc.uri, format)
        val coverPath = meta.cover?.let { bitmap ->
            val saved = covers.save(coverKey(fingerprint, doc.uri), bitmap)
            bitmap.recycle()
            saved
        }

        val entity = BookEntity(
            uri = doc.uri.toString(),
            folderId = folderId,
            docId = doc.docId,
            relPath = doc.relPath,
            fileName = doc.name,
            title = meta.title?.takeIf { it.isNotBlank() } ?: titleFromFileName(doc.name),
            author = meta.author.orEmpty(),
            series = meta.series.orEmpty(),
            seriesIndex = meta.seriesIndex,
            format = format.name,
            sizeBytes = doc.size,
            lastModified = doc.modified,
            fingerprint = fingerprint,
            coverPath = coverPath,
            pageCount = meta.pageCount,
            addedAt = System.currentTimeMillis(),
        )
        return try {
            books.insert(entity)
        } catch (e: SQLiteConstraintException) {
            null
        }
    }

    private suspend fun refresh(prev: BookEntity, doc: FoundDoc) {
        val format = BookFormat.fromFileName(doc.name) ?: return
        val fingerprint = Fingerprint.compute(context.contentResolver, doc.uri, doc.size).orEmpty()
        val meta = extractor.extract(doc.uri, format)
        val newCover = meta.cover?.let { bitmap ->
            val saved = covers.save(coverKey(fingerprint, doc.uri), bitmap)
            bitmap.recycle()
            saved
        }
        books.applyRescan(
            id = prev.id,
            size = doc.size,
            modified = doc.modified,
            fp = fingerprint,
            pages = meta.pageCount,
            cover = newCover ?: prev.coverPath,
            title = meta.title?.takeIf { it.isNotBlank() } ?: titleFromFileName(doc.name),
            author = meta.author.orEmpty(),
            series = meta.series.orEmpty(),
            seriesIndex = meta.seriesIndex,
        )
        if (newCover != null && prev.coverPath != null && prev.coverPath != newCover) {
            deleteCoverIfOrphan(prev.coverPath)
        }
    }

    // Adds individually picked files (ACTION_OPEN_DOCUMENT). 
    suspend fun importFiles(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val doc = scanner.describe(uri)
            if (doc == null || BookFormat.fromFileName(doc.name) == null) {
                skipped++
                continue
            }
            val existing = books.getByUri(uri.toString())
            if (existing != null) {
                if (existing.trashed) books.restore(existing.id)
                if (existing.missing) books.setMissing(existing.id, false)
                skipped++
                continue
            }
            if (addOrRevive(doc, null, emptySet()) != null) added++ else skipped++
        }
        ImportResult(added, skipped)
    }

    // user actions

    suspend fun toggleFavorite(id: Long) = books.toggleFavorite(id)
    suspend fun toggleToRead(id: Long) = books.toggleToRead(id)
    suspend fun toggleFinished(id: Long) = books.toggleFinished(id)
    suspend fun trash(id: Long) = books.trash(id, System.currentTimeMillis())
    suspend fun restore(id: Long) = books.restore(id)
    suspend fun restoreAll() = books.restoreAll()
    suspend fun editMeta(id: Long, title: String, author: String, series: String) =
        books.editMeta(id, title.trim().ifEmpty { "Untitled" }, author.trim(), series.trim())
    suspend fun markOpened(id: Long) = books.markOpened(id, System.currentTimeMillis())
    suspend fun setProgress(id: Long, progress: Float) = books.setProgress(id, progress.coerceIn(0f, 1f))

    // collections

    fun observeCollections(): Flow<List<CollectionWithCount>> = collections.observeWithCount()

    fun observeAllCollections(): Flow<List<CollectionEntity>> = collections.observeAll()

    fun observeCollectionIds(bookId: Long): Flow<List<Long>> = collections.observeIdsForBook(bookId)

    // Returns the new id, the id of an existing collection with that name, or -1 for a blank name. 
    suspend fun createCollection(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        collections.byName(clean)?.let { return it.id }
        val id = collections.insert(CollectionEntity(name = clean, createdAt = System.currentTimeMillis()))
        return if (id > 0) id else collections.byName(clean)?.id ?: -1L
    }

    // False when the name is blank or already used by another collection. 
    suspend fun renameCollection(id: Long, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val clash = collections.byName(clean)
        if (clash != null && clash.id != id) return false
        collections.rename(id, clean)
        return true
    }

    suspend fun deleteCollection(id: Long) {
        collections.clearMembers(id)
        collections.delete(id)
    }

    suspend fun setInCollection(bookId: Long, collectionId: Long, member: Boolean) {
        if (member) collections.addRef(BookCollectionRef(bookId, collectionId))
        else collections.removeRef(bookId, collectionId)
    }

    // helpers

    private suspend fun deleteCoverIfOrphan(path: String?) {
        if (path != null && books.coverRefs(path) == 0) covers.delete(path)
    }

    private fun coverKey(fingerprint: String, uri: Uri): String =
        fingerprint.ifEmpty {
            MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

    private fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { name }
}
