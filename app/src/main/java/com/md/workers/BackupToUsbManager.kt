package com.md.workers

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.edit
import androidx.work.*
import com.md.MemPrimeManager
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


object BackupToUsbManager {
    const val REQUEST_CODE_FOR_LOCATION_1 = 69
    const val REQUEST_CODE_FOR_LOCATION_2 = 70
    const val REQUEST_CODE_FOR_LOCATION_3 = 71
    const val REQUEST_CODE_FOR_LOCATION_4 = 72

    const val BACKUP_LOCATION_FILE = "backup_locations_prefs"
    const val BACKUP_LOCATION_KEY_1 = "backup_location_key"
    const val BACKUP_LOCATION_KEY_2 = "backup_location_key_2"
    const val BACKUP_LOCATION_KEY_3 = "backup_location_key_3"
    const val BACKUP_LOCATION_KEY_4 = "backup_location_key_4"

    val requestCodeToKey = mapOf(
            REQUEST_CODE_FOR_LOCATION_1 to BACKUP_LOCATION_KEY_1,
            REQUEST_CODE_FOR_LOCATION_2 to BACKUP_LOCATION_KEY_2,
            REQUEST_CODE_FOR_LOCATION_3 to BACKUP_LOCATION_KEY_3,
            REQUEST_CODE_FOR_LOCATION_4 to BACKUP_LOCATION_KEY_4
    )

    fun isBackupFresh(context: Context, key: String) : Boolean {
      return context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE).getBoolean(key + "is_stale", false)
    }

    fun markBackupFresh(context: Context, key: String, newValue: Boolean) {
        context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE).edit {
         putBoolean(key + "is_stale", newValue)
        }
    }


    const val BACKUP_WORK_NAME = "BACKUP_WORK_NAME"

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
        val sharedPref = context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)

        val backupLocations = mutableMapOf<String, Uri>()
        requestCodeToKey.values.forEach { locationKey ->
            if (!isBackupFresh(context, locationKey)) {
                sharedPref.getString(locationKey, null)?.let {
                    backupLocations.put(locationKey, Uri.parse(it))
                }
            }
        }

        if (backupLocations.isNotEmpty()) {
            backupToUris(context, contentResolver, backupLocations, shouldSpeak)
        } else {
            TtsSpeaker.speak("No previous backup location")
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

        val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

        val request = PeriodicWorkRequest.Builder(
                BackupWorker::class.java,
                6, TimeUnit.HOURS,
                2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        // Schedule a backup and replace the old backup.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
        )

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

            if (shouldSpeak) TtsSpeaker.speak("backup finished: " + deferred.await())
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
            } catch (e : FileNotFoundException) {
                System.err.println("Missing file during backup: $uri")
            } catch (e : SecurityException) {
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
                    } catch (e : FileNotFoundException) {
                        System.err.println("Missing file during backup: $uri")
                    } catch (e : SecurityException) {
                        if (shouldSpeak) TtsSpeaker.speak("security exception for $uri")
                    }
                }
            }
        }
    }
}

