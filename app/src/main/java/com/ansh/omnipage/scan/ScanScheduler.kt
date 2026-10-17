package com.ansh.omnipage.scan

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ScanState(val active: Boolean = false, val found: Int = 0)

class ScanScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    // Re-scan every registered folder. Ignored while a "scan all" is already queued or running.
    fun scanAll() {
        workManager.enqueueUniqueWork("scan_all", ExistingWorkPolicy.KEEP, request(-1L))
    }

    fun scanFolder(folderId: Long) {
        workManager.enqueueUniqueWork("scan_folder_$folderId", ExistingWorkPolicy.KEEP, request(folderId))
    }

    val state: Flow<ScanState> = workManager.getWorkInfosByTagFlow(TAG).map { infos ->
        val running = infos.filter { it.state == WorkInfo.State.RUNNING }
        ScanState(
            active = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED },
            found = running.maxOfOrNull { it.progress.getInt(ScanWorker.KEY_FOUND, 0) } ?: 0,
        )
    }

    private fun request(folderId: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(workDataOf(ScanWorker.KEY_FOLDER_ID to folderId))
            .addTag(TAG)
            .build()

    private companion object {
        const val TAG = "omnipage_scan"
    }
}
