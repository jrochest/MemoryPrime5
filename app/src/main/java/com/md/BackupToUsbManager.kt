package com.md

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat.startActivityForResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


object BackupToUsbManager {
    const val REQUEST_CODE = 69

    fun openZipFileDocument(activity: Activity) {
        val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        exportIntent.addCategory(Intent.CATEGORY_OPENABLE)
        exportIntent.type = "application/zip"
        val currentTimeMillis = System.currentTimeMillis()
        val filename = "memprime_note_$currentTimeMillis.zip"
        exportIntent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(activity, exportIntent, REQUEST_CODE, null)
    }


    fun createAndWriteZipBackup(
            context: Context,
            data: Intent,
            requestCode: Int,
            contentResolver: ContentResolver
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false

        val sourceTreeUri: Uri = data.data ?: return true
        contentResolver.takePersistableUriPermission(
                sourceTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        context.filesDir.listFiles().forEach {
            if (it.isDirectory && it.name == "com.md.MemoryPrime") {
                val filesToZip = mutableListOf<String>()
                it.listFiles().forEach { databaseOrAudioDirectory ->
                    if (databaseOrAudioDirectory.isDirectory) {
                        databaseOrAudioDirectory.listFiles().forEach { audioDirs ->
                            if (audioDirs.isDirectory) {
                                audioDirs.listFiles().forEach { audioFile ->
                                    // Audio files
                                    filesToZip.add(audioFile.path)
                                }
                            }
                        }
                    } else {
                        // Files only. No directories
                        filesToZip.add(databaseOrAudioDirectory.path)
                    }
                }

                contentResolver.openFileDescriptor(sourceTreeUri, "w")?.use {
                    val output = FileOutputStream(it.fileDescriptor) ?: return@use
                    ZipManager.zip(filesToZip.toTypedArray(), output)
                }
            }
        }
        return false
    }

}

