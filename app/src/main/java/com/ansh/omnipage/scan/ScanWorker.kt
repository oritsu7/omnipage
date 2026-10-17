package com.ansh.omnipage.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ansh.omnipage.OmniPageApp
import kotlin.coroutines.cancellation.CancellationException

class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as OmniPageApp).container.repository
        val one = inputData.getLong(KEY_FOLDER_ID, -1L)
        val ids = if (one >= 0) listOf(one) else repository.folderIds()

        return try {
            for (id in ids) {
                repository.scanFolder(id) { n -> setProgress(workDataOf(KEY_FOUND to n)) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_FOUND = "found"
    }
}
