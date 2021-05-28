package com.md.workers

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import com.md.DbNoteEditor
import com.md.MemPrimeManager
import com.md.NotesProvider
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import com.md.utils.ToastSingleton
import com.md.workers.BackupToUsbManager.UPDATE_TIME_FILE_NAME
import com.md.workers.BackupToUsbManager.markAudioDirectoryWithUpdateTime
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

object IncrementalBackupManager {
    fun openBackupDir(activity: Activity, requestCode: Int) {
        TtsSpeaker.speak(
            "Create a new directory where you would like backup files to be" +
                    " written to. Then click allow to grant this app access."
        )
        // Choose a directory using the system's file picker.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Provide write access to files and sub-directories in the user-selected
            // directory.
            flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        ActivityCompat.startActivityForResult(activity, intent, requestCode, null)
    }

    fun createAndWriteZipBackToPreviousLocation(
        context: SpacedRepeaterActivity,
        contentResolver: ContentResolver,
        shouldSpeak: Boolean
    ) {
        val backupLocations = IncrementalBackupPreferences.getBackupLocations(context)

        if (backupLocations.isNotEmpty()) {
            backupToUris(context, contentResolver, backupLocations, shouldSpeak)
        } else {
            TtsSpeaker.speak("No backup needed")
        }
    }

    fun createAndWriteZipBackToNewLocation(
        context: SpacedRepeaterActivity,
        data: Intent,
        requestCode: Int,
        contentResolver: ContentResolver
    ): Boolean {
        val locationKey: String = IncrementalBackupPreferences.requestCodeToKey.get(requestCode)
            ?: return false
        val sourceTreeUri: Uri = data.data ?: return false

        contentResolver.takePersistableUriPermission(
            sourceTreeUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val sharedPref = context.getSharedPreferences(
            IncrementalBackupPreferences.BACKUP_LOCATION_FILE, Context.MODE_PRIVATE
        )

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

    private suspend fun backupOnBackground(
        contentResolver: ContentResolver,
        backupUris: MutableMap<String, Uri>,
        filesDir: File,
        shouldSpeak: Boolean,
        context: Context
    ) {

        val validBackupUris = backupUris.filter { uri ->
            try {
                if (DocumentFile.fromTreeUri(context, uri.value)?.isDirectory == true) {
                    return@filter true
                }
            } catch (e: FileNotFoundException) {
                System.err.println("Missing file during backup: $uri")
            } catch (e: SecurityException) {
                if (shouldSpeak) TtsSpeaker.speak("security exception for $uri")
            }
            false
        }
        val backupsNeeded = validBackupUris.size
        if (shouldSpeak) TtsSpeaker.speak("backups needed $backupsNeeded")
        if (backupsNeeded == 0) {
            return
        }

        val allFiles = filesDir.listFiles()
        if (allFiles == null || allFiles.isEmpty()) {
            TtsSpeaker.error("All files empty")
            return
        }

        val openHelper = NotesProvider.mOpenHelper
        openHelper?.close()

        val audioDirectoryToAudioFiles = mutableMapOf<String, List<File>>()
        var databaseLastModTime: Long? = 0
        val audioDirectoryToModificationTime = mutableMapOf<String, Long>()
        allFiles.forEach {
            if (it.isDirectory && it.name == "com.md.MemoryPrime") {
                val dirsToZip = mutableListOf<File>()
                val databaseFilesToZip = mutableListOf<File>()
                val databaseOrAudioDirectory = it.listFiles()
                if (databaseOrAudioDirectory == null || databaseOrAudioDirectory.isEmpty()) {
                    TtsSpeaker.error("no data base or audio directory")
                    return
                }

                databaseOrAudioDirectory.forEach { databaseOrAudioDirectory ->
                    if (databaseOrAudioDirectory.isDirectory) {
                        val audioDirectory = databaseOrAudioDirectory.listFiles()
                        if (audioDirectory == null || audioDirectory.isEmpty()) {
                            TtsSpeaker.error("audio directory empty")
                            return
                        }
                        audioDirectory.forEach { audioDirs ->
                            if (audioDirs.isDirectory) {
                                val updateTimeFile = File(audioDirs, UPDATE_TIME_FILE_NAME)
                                if (File(audioDirs, UPDATE_TIME_FILE_NAME).exists()) {
                                    audioDirectoryToModificationTime.put(
                                        audioDirs.name,
                                        updateTimeFile.lastModified()
                                    )
                                } else {
                                    markAudioDirectoryWithUpdateTime(audioDirs)
                                }
                                audioDirectoryToAudioFiles.put(
                                    audioDirs.name,
                                    audioDirs.listFiles().filter { it.isFile })
                            } else {
                                TtsSpeaker.error("Audio directory contained unknown file")
                            }
                        }
                    } else { // Else it's the database.
                        // Files only. No directories
                        if (databaseOrAudioDirectory.name.endsWith(".db")) {
                            // This if adds memory_droid.db, but not memory_droid.db-journal
                            databaseLastModTime = databaseOrAudioDirectory.lastModified()
                            databaseFilesToZip.add(databaseOrAudioDirectory)
                        } else {
                            println("backup ignoring non-db file: >$databaseOrAudioDirectory<")
                        }
                    }
                }

                for (uri: Map.Entry<String, Uri> in validBackupUris) {
                    try {
                        val backupRoot = DocumentFile.fromTreeUri(context, uri.value)
                        if (backupRoot == null) {
                            TtsSpeaker.error("Couldn't open backup dir")
                            continue
                        }


                        val oldOldDatabaseFile = backupRoot.findFile("database.zip.last")
                        if (oldOldDatabaseFile?.exists() == true) {
                            oldOldDatabaseFile.delete()
                        }

                        val oldDatabaseFile = backupRoot.findFile("database.zip")
                        if (oldDatabaseFile?.exists() == true) {
                            oldDatabaseFile.renameTo("database.zip.last")
                        }

                        var databaseZip: DocumentFile? = null
                        for (attempt in 1..3) {
                            databaseZip = backupRoot.createFile("application/zip", "database.zip")
                            if (databaseZip == null) {
                                TtsSpeaker.error("Database backup create failed. Try $attempt")
                            } else {
                                break
                            }
                        }

                        if (databaseZip == null) {
                            TtsSpeaker.error("Database backup failed repeatedly for " + uri.key)
                            continue
                        }

                        val fileDescriptor =
                            contentResolver.openFileDescriptor(databaseZip.uri, "w")
                        val descriptor = fileDescriptor?.fileDescriptor
                        if (descriptor == null) {
                            TtsSpeaker.error("Database zipping failed for missing file " + uri.key)
                            continue
                        }
                        val output = FileOutputStream(descriptor)
                        println("zipping database $databaseFilesToZip")
                        if (MemPrimeManager.zip(databaseFilesToZip, dirsToZip, output)) {
                            println("Backed up database successful")
                        } else {
                            TtsSpeaker.error("Database zipping failed for " + uri.key)
                            continue
                        }
                        fileDescriptor.close()

                        // Now delete
                        if (oldDatabaseFile?.exists() == true) {
                            oldDatabaseFile.delete()
                        }

                        val taskList = mutableListOf<Deferred<Boolean>>()
                        audioDirectoryToAudioFiles.forEach { (dirName, fileList) ->
                            taskList.add(GlobalScope.async(Dispatchers.IO) {
                                val previousBackup = backupRoot.findFile("$dirName.zip")

                                if (previousBackup?.exists() == true) {
                                    if (previousBackup.length() == 0L) {
                                        // If there is an empty backup zip. Write the file again.
                                        TtsSpeaker.error("Fixing empty $dirName")
                                        previousBackup.delete()
                                    } else {
                                        val lastDirMod = audioDirectoryToModificationTime[dirName]
                                        if (lastDirMod == null) {
                                            if (fileList.indexOfFirst { it.lastModified() >= previousBackup.lastModified() } == -1) {
                                                println("Done Search times stamps for $dirName none")
                                                return@async false
                                            }
                                        } else {
                                            if (lastDirMod < previousBackup.lastModified()) {
                                                println("Done No Search needed for $dirName")
                                                return@async false
                                            }
                                        }
                                    } // else out of date. Recreate backup.

                                    previousBackup.delete()
                                }

                                val dirZip =
                                    backupRoot.createFile("application/zip", "$dirName.zip")
                                if (dirZip == null) {
                                    TtsSpeaker.error("Couldn't create audio backup file $dirName")
                                } else {
                                    contentResolver.openFileDescriptor(dirZip.uri, "w")?.use {
                                        val output = FileOutputStream(it.fileDescriptor)
                                        if (MemPrimeManager.zip(fileList, dirsToZip, output)) {
                                            GlobalScope.launch(Dispatchers.Main) {
                                                ToastSingleton.getInstance()
                                                    .msg("Memprime backed up $dirName")
                                            }
                                            return@async true
                                        } else {
                                            GlobalScope.launch(Dispatchers.Main) {
                                                TtsSpeaker.error("zip write failed audio backup file $dirName")
                                            }
                                        }
                                    }
                                }
                                return@async false
                            })
                        }
                        taskList.forEach { it.await() }
                        if (shouldSpeak) TtsSpeaker.speak("Backup finished for" + uri.key)
                    } catch (e: FileNotFoundException) {
                        if (shouldSpeak) TtsSpeaker.speak("FileNotFoundException for " + uri.key)
                        System.err.println("Missing file during backup: $uri")
                    } catch (e: SecurityException) {
                        if (shouldSpeak) TtsSpeaker.speak("security exception for $uri" + uri.key)
                    }
                }
            }
        }
    }
}