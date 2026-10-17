package com.ansh.omnipage.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ansh.omnipage.data.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class FoundDoc(
    val uri: Uri,
    val docId: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val relPath: String,
)

data class ScanResult(val docs: List<FoundDoc>, val complete: Boolean)

// Walks a SAF tree with raw DocumentsContract queries (≈10× faster than DocumentFile.listFiles). 
class FolderScanner(private val context: Context) {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    suspend fun enumerate(tree: Uri, onProgress: suspend (Int) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val out = ArrayList<FoundDoc>()
            var complete = true
            val stack = ArrayDeque<Pair<String, Int>>()
            stack.addLast(DocumentsContract.getTreeDocumentId(tree) to 0)

            while (stack.isNotEmpty()) {
                ensureActive()
                val (parentId, depth) = stack.removeLast()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                try {
                    val cursor = resolver.query(children, projection, null, null, null)
                    if (cursor == null) {
                        complete = false
                    } else {
                        cursor.use { c ->
                            while (c.moveToNext()) {
                                val docId = c.getString(0) ?: continue
                                val name = c.getString(1) ?: continue
                                val mime = c.getString(2)
                                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    if (depth < MAX_DEPTH && !name.startsWith(".")) stack.addLast(docId to depth + 1)
                                } else if (BookFormat.fromFileName(name) != null) {
                                    out.add(
                                        FoundDoc(
                                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                                            docId = docId,
                                            name = name,
                                            size = c.getLong(3),
                                            modified = c.getLong(4),
                                            relPath = relPathOf(docId),
                                        )
                                    )
                                    if (out.size % 25 == 0) onProgress(out.size)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    complete = false
                }
            }
            onProgress(out.size)
            ScanResult(out, complete)
        }

    // Describes a single document URI (used for individually picked files). 
    fun describe(uri: Uri): FoundDoc? = try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                val docId = c.getString(0) ?: ""
                val name = c.getString(1) ?: return@use null
                FoundDoc(uri, docId, name, c.getLong(3), c.getLong(4), relPathOf(docId))
            }
        }
    } catch (e: Exception) {
        null
    }

    fun exists(uri: Uri): Boolean = try {
        context.contentResolver
            .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        false
    }

    private fun relPathOf(docId: String): String = docId.substringAfter(':', docId)

    private companion object {
        const val MAX_DEPTH = 12
    }
}
