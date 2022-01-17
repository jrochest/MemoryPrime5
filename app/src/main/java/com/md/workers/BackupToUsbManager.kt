package com.md.workers

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat.startActivityForResult
import com.md.AudioPlayer
import com.md.MemPrimeManager
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import com.md.workers.BackupPreferences.BACKUP_LOCATION_FILE
import com.md.workers.BackupPreferences.markBackupFresh
import com.md.workers.BackupPreferences.requestCodeToKey
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.PrintWriter


object BackupToUsbManager {
    const val BACKUP_WORK_NAME = "BACKUP_WORK_NAME_AFTER"

    fun openZipFileDocument(activity: Activity, requestCode: Int) {
        val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        exportIntent.addCategory(Intent.CATEGORY_OPENABLE)
        exportIntent.type = "application/zip"
        val currentTimeMillis = System.currentTimeMillis()
        val filename = "memprime_note_$currentTimeMillis.zip"
        exportIntent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(activity, exportIntent, requestCode, null)
    }

    fun createAndWriteZipBackToPreviousLocation(
            context: Context,
            contentResolver: ContentResolver,
            shouldSpeak: Boolean = false
    ): Boolean {
        val backupLocations = BackupPreferences.getBackupLocations(context)

        if (backupLocations.isNotEmpty()) {
            backupToUris(context, contentResolver, backupLocations, shouldSpeak)
        } else {
            TtsSpeaker.speak("No backup needed")
            return false
        }

        return true
    }

    fun createAndWriteZipBackToNewLocation(
            context: SpacedRepeaterActivity,
            data: Intent,
            requestCode: Int,
            contentResolver: ContentResolver
    ): Boolean {
        val locationKey: String = requestCodeToKey.get(requestCode) ?: return false
        val sourceTreeUri: Uri = data.data ?: return false
        contentResolver.takePersistableUriPermission(
                sourceTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val sharedPref = context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)
        sharedPref.edit().putString(locationKey, sourceTreeUri.toString()).apply()

        return true
    }

    private fun backupToUris(
            context: Context,
            contentResolver: ContentResolver,
            backupUris: MutableMap<String, Uri>,
            shouldSpeak: Boolean = false
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            if (shouldSpeak) TtsSpeaker.speak("starting backup")

            val deferred = async(Dispatchers.IO) {
                backupOnBackground(
                        contentResolver,
                        backupUris,
                        context.filesDir,
                        shouldSpeak,
                        context
                )
            }

            deferred.await()
            // We don't really need this due to the toasts.
            // if (shouldSpeak) TtsSpeaker.speak("backup finished: " + deferred.await())
        }
    }

    private suspend fun backupOnBackground(contentResolver: ContentResolver, backupUris: MutableMap<String, Uri>, filesDir: File, shouldSpeak: Boolean, context: Context) {
        var backupsNeeded = 0
        for (uri: Map.Entry<String, Uri> in backupUris) {
            try {
                contentResolver.openFileDescriptor(uri.value, "w")?.use {
                    if (it.fileDescriptor.valid()) {
                        backupsNeeded++
                    }
                }
            } catch (e: FileNotFoundException) {
                System.err.println("Missing file during backup: $uri")
            } catch (e: SecurityException) {
                if (shouldSpeak) TtsSpeaker.speak("security exception for $uri")
            }
        }
        if (shouldSpeak) TtsSpeaker.speak("backups needed $backupsNeeded")
        if (backupsNeeded == 0) {
            return
        }


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

                for (uri: Map.Entry<String, Uri> in backupUris) {
                    try {
                        contentResolver.openFileDescriptor(uri.value, "w")?.use {
                            val output = FileOutputStream(it.fileDescriptor)
                            if (MemPrimeManager.zip(filesToZip, dirsToZip, output)) {
                                markBackupFresh(context, uri.key, newValue = true)
                            }
                        }
                    } catch (e: FileNotFoundException) {
                        System.err.println("Missing file during backup: $uri")
                    } catch (e: SecurityException) {
                        if (shouldSpeak) TtsSpeaker.speak("security exception for $uri")
                    }
                }
            }
        }
    }

    const val UPDATE_TIME_FILE_NAME = "updateTime.txt"

    fun markPathAsUpdated(originalFilePath: String) {
        val audioDirectory = AudioPlayer.getAudioDirectory(originalFilePath)

        markAudioDirectoryWithUpdateTime(File(audioDirectory))
    }

    fun markAudioDirectoryWithUpdateTime(audioDirectory: File) {
        GlobalScope.launch(Dispatchers.IO) {
            PrintWriter(File(audioDirectory,UPDATE_TIME_FILE_NAME)).use {
                it.println(System.currentTimeMillis())
            }
        }
    }
}

