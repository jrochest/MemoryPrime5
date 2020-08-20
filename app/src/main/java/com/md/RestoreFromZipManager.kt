package com.md

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat.startActivityForResult
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object RestoreFromZipManager {
    const val REQUEST_CODE = 70

    fun openZipFileDocument(activity: Activity) {
        val exportIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        exportIntent.addCategory(Intent.CATEGORY_OPENABLE)
        exportIntent.type = "application/zip"
        startActivityForResult(activity, exportIntent, REQUEST_CODE, null)
    }

    fun restoreFromZip(
            activity: SpacedRepeaterActivity,
            data: Intent,
            requestCode: Int,
            contentResolver: ContentResolver
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false


        val sourceTreeUri: Uri = data.data ?: return false
        contentResolver.takePersistableUriPermission(
                sourceTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        GlobalScope.launch(Dispatchers.Main) {
            activity.backupTone()

            val deferred = async(Dispatchers.IO) {
                restoreInBackground(contentResolver, sourceTreeUri, activity.filesDir)
            }

            TtsSpeaker.speak("Restore finished. Finishing activity" + deferred.await())

            activity.finish()
        }

        return true
    }

    @Throws(IOException::class)
    fun unzip(zipStream: FileInputStream, targetDirectory: File) {
        val zis = ZipInputStream(BufferedInputStream(zipStream))
        var filesRestored = 0
        try {
            var ze: ZipEntry?
            var count: Int
            val buffer = ByteArray(8192)

            while (true) {
                ze = zis.nextEntry
                if (ze == null) break

                val file = File(targetDirectory, ze.getName())
                val dir = if (ze.isDirectory()) file else file.parentFile
                if (!dir.isDirectory && !dir.mkdirs()) throw FileNotFoundException("Failed to ensure directory: " +
                        dir.absolutePath)
                if (ze.isDirectory()) {
                    continue
                }
                val fout = FileOutputStream(file)
                try {
                    while (zis.read(buffer).also { count = it } != -1) fout.write(buffer, 0, count)
                    filesRestored++
                    if (filesRestored % 1000 == 0) {
                        System.out.println("Restored count: " + filesRestored)
                    }
                } finally {
                    fout.close()
                }
            }
            TtsSpeaker.speak("Restored files $filesRestored")
        } catch (e: Exception) {
            TtsSpeaker.speak("Failed to restore files " + e.message)
        }
        finally {
            zis.close()
        }
    }

    private fun restoreInBackground(contentResolver: ContentResolver, sourceTreeUri: Uri, filesDir: File) {
        val memPrimeDir = File(filesDir, "com.md.MemoryPrime")
        val audioMemo = File(memPrimeDir, "AudioMemo")

        if (audioMemo.exists()) {
            TtsSpeaker.speak("Audio memo dir is not empty. Clear data first!")
            return
        }
        TtsSpeaker.speak("Restoring!")
        // This moves the database that is created due to starting up the app the first time.
        memPrimeDir.renameTo(File(filesDir, "com.md.MemoryPrime.Old"))

        contentResolver.openFileDescriptor(sourceTreeUri, "r")?.use {
            val output = FileInputStream(it.fileDescriptor) ?: return@use
            unzip(output, filesDir)
        }
    }
}

