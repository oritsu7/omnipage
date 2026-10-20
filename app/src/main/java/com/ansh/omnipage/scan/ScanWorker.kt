package com.ansh.omnipage.scan

import android.content.Context
import android.os.SystemClock
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

        // Each setProgress is a database write that re-renders the library, so don't spam it.
        var lastReport = 0L

        return try {
            for (id in ids) {
                repository.scanFolder(id) { n ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                        lastReport = now
                        setProgress(workDataOf(KEY_FOUND to n))
                    }
                }
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
        private const val PROGRESS_INTERVAL_MS = 400L
    }
}