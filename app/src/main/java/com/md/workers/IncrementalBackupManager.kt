package com.md.workers

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import com.md.MemPrimeManager
import com.md.NotesProvider
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import com.md.utils.ToastSingleton
import com.md.workers.BackupToUsbManager.UPDATE_TIME_FILE_NAME
import com.md.workers.BackupToUsbManager.markAudioDirectoryWithUpdateTime
import kotlinx.coroutines.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream


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
            shouldSpeak: Boolean,
            runExtraValidation: Boolean = false,
            onFinished: ((Boolean) -> Unit)? = null
    ) {
        val backupLocations = IncrementalBackupPreferences.getBackupLocations(context)

        if (backupLocations.isNotEmpty()) {
            backupToUris(context, contentResolver, backupLocations, shouldSpeak, runExtraValidation, onFinished)
        } else {
            TtsSpeaker.speak("No backup needed")
            onFinished?.invoke(false)
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
            shouldSpeak: Boolean = false,
            runExtraValidation: Boolean,
            onFinished: ((Boolean) -> Unit)?
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            if (shouldSpeak) TtsSpeaker.speak("starting backup")
            launch(Dispatchers.IO) {
                val result = backupOnBackground(
                    contentResolver,
                    backupUris,
                    context.filesDir,
                    shouldSpeak,
                    context,
                    runExtraValidation
                )
                launch (Dispatchers.Main) {
                    onFinished?.invoke(result)
                }
            }
        }
    }

    private suspend fun backupOnBackground(
            contentResolver: ContentResolver,
            backupUris: MutableMap<String, Uri>,
            appStorageRoot: File,
            shouldSpeak: Boolean,
            context: Context,
            runExtraValidation: Boolean
    ) : Boolean {
        var success = false
        val validBackupUris = backupUris.filter { uri ->
            try {
                if (DocumentFile.fromTreeUri(context, uri.value)?.isDirectory == true) {
                    return@filter true
                }
            } catch (e: FileNotFoundException) {
                if (shouldSpeak) TtsSpeaker.speak("missing exception for $uri")
            } catch (e: SecurityException) {
                if (shouldSpeak) TtsSpeaker.speak("security exception for $uri")
            }
            false
        }
        val backupsNeeded = validBackupUris.size
        if (shouldSpeak) TtsSpeaker.speak("backups needed $backupsNeeded")
        if (backupsNeeded == 0) {
            return false
        }

        val allFiles = appStorageRoot.listFiles()
        if (allFiles == null || allFiles.isEmpty()) {
            TtsSpeaker.error("All files empty")
            return false
        }

        val openHelper = NotesProvider.mOpenHelper
        openHelper?.close()

        val audioDirectoryToAudioFiles = mutableMapOf<String, List<File>>()
        val audioDirectoryToModificationTime = mutableMapOf<String, Long>()
        allFiles.forEach { it ->
            if (it.isDirectory && it.name == "com.md.MemoryPrime") {
                val dirsToZip = mutableListOf<File>()
                var databaseFileOrNull: File? = null
                val databaseOrAudioDirectoryList = it.listFiles()
                if (databaseOrAudioDirectoryList == null || databaseOrAudioDirectoryList.isEmpty()) {
                    TtsSpeaker.error("no data base or audio directory")
                    return false
                }

                databaseOrAudioDirectoryList.forEach { databaseOrAudioDirectory ->
                    if (databaseOrAudioDirectory.isDirectory) {
                        // com.md.MemoryPrime/AudioMemo/
                        val audioDirectoryList = databaseOrAudioDirectory.listFiles()
                        // com.md.MemoryPrime/AudioMemo/1
                        // com.md.MemoryPrime/AudioMemo/2
                        if (audioDirectoryList == null || audioDirectoryList.isEmpty()) {
                            TtsSpeaker.error("numbered audio directory empty")
                            return false
                        }
                        audioDirectoryList.forEach { numberedAudioDir ->
                            if (numberedAudioDir.isDirectory) {
                                val updateTimeFile = File(numberedAudioDir, UPDATE_TIME_FILE_NAME)
                                if (updateTimeFile.exists()) {
                                    audioDirectoryToModificationTime.put(
                                        numberedAudioDir.name,
                                        updateTimeFile.lastModified()
                                    )
                                } else {
                                    markAudioDirectoryWithUpdateTime(numberedAudioDir)
                                }
                                audioDirectoryToAudioFiles.put(
                                    numberedAudioDir.name,
                                    numberedAudioDir.listFiles().filter { it.isFile })
                            } else {
                                TtsSpeaker.error("Audio directory contained unknown file")
                            }
                        }
                    } else { // Else it's the database.
                        // Files only. No directories
                        if (databaseOrAudioDirectory.name.equals("memory_droid.db")) {
                            // This if adds memory_droid.db, but not memory_droid.db-journal
                            databaseFileOrNull = databaseOrAudioDirectory
                        } else {
                            println("backup ignoring non-db file: >$databaseOrAudioDirectory<")
                        }
                    }
                }


                val databaseFile = databaseFileOrNull
                if (databaseFile == null) {
                    TtsSpeaker.error("No database file! ")
                    return false
                }

                // consider it a success if just one succeeds.
                success = success or zipBackup(validBackupUris, context, contentResolver, mutableListOf(databaseFile), dirsToZip, audioDirectoryToAudioFiles, runExtraValidation, audioDirectoryToModificationTime, shouldSpeak)
            }
        }
        return success
    }

    private suspend fun zipBackup(validBackupUris: Map<String, Uri>, context: Context, contentResolver: ContentResolver, databaseFilesToZip: MutableList<File>, dirsToZip: MutableList<File>, audioDirectoryToAudioFiles: MutableMap<String, List<File>>, runExtraValidation: Boolean, audioDirectoryToModificationTime: MutableMap<String, Long>, shouldSpeak: Boolean) : Boolean {
        var success = false
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

                contentResolver.openFileDescriptor(databaseZip.uri, "w")!!.use {
                    val descriptor = it.fileDescriptor
                    if (descriptor == null) {
                        TtsSpeaker.error("Database zipping failed for missing file " + uri.key)
                    } else {
                        val output = FileOutputStream(descriptor)
                        println("zipping database $databaseFilesToZip")
                        if (MemPrimeManager.zip(databaseFilesToZip, dirsToZip, output)) {
                            println("Backed up database successful")
                        } else {
                            TtsSpeaker.error("Database zipping failed for " + uri.key)
                        }
                    }
                }

                // Now delete
                if (oldDatabaseFile?.exists() == true) {
                    oldDatabaseFile.delete()
                }

                val taskList = mutableListOf<Deferred<Boolean>>()
                audioDirectoryToAudioFiles.forEach { (dirName, fileList) ->
                    taskList.add(GlobalScope.async(Dispatchers.IO) {
                        // This block returns early if it determines a new audio file zip is not
                        // needed for the directory.
                        val previousBackup = backupRoot.findFile("$dirName.zip")
                        if (previousBackup != null && previousBackup.exists()) {
                            if (previousBackup.length() == 0L) {
                                // If there is an empty backup zip. Write the file again.
                                TtsSpeaker.speak("Empty $dirName")
                                previousBackup.delete()
                            } else if (runExtraValidation && !isZipValidAndHasExpectedAudioFiles(previousBackup, contentResolver, fileList)) {
                                // If there is an empty backup zip. Write the file again.
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
                                        println("$dirName phone audio dir modification time before zip for audio dir")
                                        return@async false
                                    }
                                }
                            } // else out of date. Recreate backup.
                            previousBackup.delete()
                        }

                        // Create the specific audio directory's zip.
                        val dirZip =
                                backupRoot.createFile("application/zip", "$dirName.zip")
                        if (dirZip == null) {
                            TtsSpeaker.error("Couldn't create audio backup file $dirName")
                        } else {
                            val descriptor = contentResolver.openFileDescriptor(dirZip.uri, "w")
                            descriptor?.use {
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
                                // This prevents a "A resource failed to call end"
                                // it.close()
                                output.close()
                            }
                            descriptor?.close()
                        }
                        return@async false
                    })
                }
                taskList.forEach { it.await() }
                if (shouldSpeak) {
                    TtsSpeaker.speak("Backup finished for" + uri.key)
                }
                // consider it a success if one backup finishes without issue.
                success = true
            } catch (e: FileNotFoundException) {
                if (shouldSpeak) TtsSpeaker.speak("FileNotFoundException for " + uri.key)
                System.err.println("Missing file during backup: $uri")
                return false
            } catch (e: SecurityException) {
                if (shouldSpeak) TtsSpeaker.speak("security exception for $uri" + uri.key)
                return false
            }
        }
        return success
    }

    private fun isZipValidAndHasExpectedAudioFiles(
        zipToValidate: DocumentFile,
        contentResolver: ContentResolver,
        expectedFileList: List<File>
    ): Boolean {
        val expectedCount = expectedFileList.size
        var count = 0
        val openFileDescriptor = contentResolver.openFileDescriptor(zipToValidate.uri, "r") ?: return false
        // The use of ".use" prevents a "A resource failed to call close."
        openFileDescriptor.use {
            var zis: ZipInputStream? = null
            var fis: InputStream? = null
            return try {
                fis = FileInputStream(it.fileDescriptor)
                zis = ZipInputStream(fis)
                var ze: ZipEntry? = zis.nextEntry
                while (ze != null) {
                    count++
                    // if it throws an exception fetching any of the following then we know the file is corrupted.
                    ze.crc
                    ze.compressedSize
                    ze.name
                    ze = zis.nextEntry
                }

                // Validate that current number of files in the directory matches the number in the zip.
                if (expectedCount == count) {
                    true
                } else {
                    TtsSpeaker.speak("zip expectedCount $expectedCount actualCount $count ")
                    false
                }
            } catch (e: ZipException) {
                TtsSpeaker.speak("zip exception")

                println("extra validation error " + e)
                false
            } catch (e: IOException) {
                TtsSpeaker.speak("IO exception")
                println("extra IO exception validation error " + e)
                false
            }
            finally {
                // fis?.close() does not seem to be needed, but the follow prevents a
                // "A resource failed to call end."
                zis?.close()
            }
        }
    }
}