package com.md

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat.startActivityForResult
import com.md.utils.ToastSingleton
import kotlinx.coroutines.*
import java.io.File
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
            context: SpacedRepeaterActivity,
            data: Intent,
            requestCode: Int,
            contentResolver: ContentResolver
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false


        val sourceTreeUri: Uri = data.data ?: return false
        contentResolver.takePersistableUriPermission(
                sourceTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        GlobalScope.launch(Dispatchers.Main) {
            context.backupDoneTone()

            val deferred = async(Dispatchers.IO) {
                backupOnBackground(contentResolver, sourceTreeUri, context.filesDir)
            }

            ToastSingleton.getInstance().msg("finished backup with" + deferred.await())
            context.backupDoneTone()
        }

        return true


    }

    private suspend fun backupOnBackground(contentResolver: ContentResolver, sourceTreeUri: Uri, filesDir: File) {
        filesDir.listFiles().forEach {
            if (it.isDirectory && it.name == "com.md.MemoryPrime") {
                val dirsToZip = mutableListOf<File>()
                val filesToZip = mutableListOf<File>()
                it.listFiles().forEach { databaseOrAudioDirectory ->
                    if (databaseOrAudioDirectory.isDirectory) {
                        dirsToZip.add(databaseOrAudioDirectory)
                        databaseOrAudioDirectory.listFiles().forEach { audioDirs ->
                            if (audioDirs.isDirectory) {
                                dirsToZip.add(audioDirs)
                                System.out.println("Adding dir" + audioDirs.path)
                                audioDirs.listFiles().forEach { audioFile ->
                                    // Audio files
                                    filesToZip.add(audioFile)
                                }
                            }
                        }
                    } else {
                        // Files only. No directories
                        filesToZip.add(databaseOrAudioDirectory)
                    }
                }

                contentResolver.openFileDescriptor(sourceTreeUri, "w")?.use {
                    val output = FileOutputStream(it.fileDescriptor) ?: return@use
                    MemPrimeManager.zip(filesToZip, dirsToZip, output)
                }
            }
        }
    }
}

