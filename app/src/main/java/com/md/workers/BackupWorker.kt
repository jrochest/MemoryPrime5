package com.md.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.md.workers.BackupToUsbManager.createAndWriteZipBackToPreviousLocation

class BackupWorker(private val appContext: Context, workerParams: WorkerParameters):
        Worker(appContext, workerParams) {
    override fun doWork(): Result {
        System.out.println("MemoryPrime BackupWorker starting")
        if (createAndWriteZipBackToPreviousLocation(appContext, appContext.contentResolver)) {
            System.out.println("MemoryPrime BackupWorker successful")
            return Result.success()
        } else {
            System.out.println("MemoryPrime BackupWorker failure")
            return Result.failure()
        }
    }
}
