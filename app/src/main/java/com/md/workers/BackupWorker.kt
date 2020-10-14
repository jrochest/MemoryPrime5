package com.md.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.md.workers.BackupToUsbManager.createAndWriteZipBackToPreviousLocation


class BackupWorker(private val appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (createAndWriteZipBackToPreviousLocation(appContext, appContext.contentResolver)) {
            return Result.success()
        } else {
            return Result.failure()
        }
    }
}
