package com.md.workers

import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter

object BackupToUsbManager {
    const val UPDATE_TIME_FILE_NAME = "updateTime.txt"
    fun markAudioDirectoryWithUpdateTime(audioDirectory: File) {
        GlobalScope.launch(Dispatchers.IO) {
            PrintWriter(File(audioDirectory, UPDATE_TIME_FILE_NAME)).use {
                it.println(System.currentTimeMillis())
            }
        }
    }
}

