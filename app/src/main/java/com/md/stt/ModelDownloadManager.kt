package com.md.stt

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object NotDownloaded : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Extracting : DownloadState()
    object Ready : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelDownloadManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val PREFS_NAME = "VoskModelPrefs"
        const val KEY_HIGH_FIDELITY_READY = "high_fidelity_model_ready"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.42-gigaspeech.zip"
        const val MODEL_ZIP_NAME = "vosk-model-en-us-0.42-gigaspeech.zip"
        const val MODEL_DIR_NAME = "vosk-model-en-us-0.42-gigaspeech"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private var progressJob: Job? = null

    private val _downloadState = MutableStateFlow<DownloadState>(if (isHighFidelityModelReady()) DownloadState.Ready else DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun isHighFidelityModelReady(): Boolean {
        return prefs.getBoolean(KEY_HIGH_FIDELITY_READY, false)
    }

    fun startDownload() {
        if (isHighFidelityModelReady()) return

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Downloading High-Fidelity Vosk Model")
            .setDescription("Downloading 2.3 GB model for offline transcription")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, MODEL_ZIP_NAME)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        downloadId = downloadManager.enqueue(request)
        _downloadState.value = DownloadState.Downloading(0f)
        startProgressPolling()
        
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    progressJob?.cancel()
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        extractModelAsync()
                    } else {
                        // Check if it failed
                        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                        var reason = "Unknown"
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            if (statusIndex != -1 && reasonIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_FAILED) {
                                reason = "Error code: ${cursor.getInt(reasonIndex)}"
                            }
                            cursor.close()
                        }
                        _downloadState.value = DownloadState.Error("Download failed. $reason")
                    }
                }
            }
        }
        
        // Register receiver (Context.RECEIVER_EXPORTED required on newer Androids, but plain registerReceiver is fine for standard apps if not specifying)
        try {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } catch (e: NoSuchMethodError) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        
                        if (bytesTotal > 0) {
                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            _downloadState.value = DownloadState.Downloading(progress)
                        }
                    }
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            cursor.close()
                            break
                        }
                    }
                    cursor.close()
                } else {
                    cursor?.close()
                }
                delay(500)
            }
        }
    }

    private fun extractModelAsync() {
        _downloadState.value = DownloadState.Extracting
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zipFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_ZIP_NAME)
                if (!zipFile.exists()) {
                    Log.e("ModelDownloadManager", "Zip file not found: ${zipFile.absolutePath}")
                    return@launch
                }

                val targetDir = File(context.filesDir, "vosk_models")
                if (!targetDir.exists()) targetDir.mkdirs()

                Log.i("ModelDownloadManager", "Starting memory-safe extraction of ${zipFile.name} to ${targetDir.absolutePath}")

                unzipFileStreaming(zipFile, targetDir)

                // Cleanup zip file
                zipFile.delete()
                Log.i("ModelDownloadManager", "Extraction complete. Zip file deleted.")

                prefs.edit().putBoolean(KEY_HIGH_FIDELITY_READY, true).apply()
                _downloadState.value = DownloadState.Ready

            } catch (e: Exception) {
                Log.e("ModelDownloadManager", "Error extracting model", e)
                _downloadState.value = DownloadState.Error("Extraction failed: ${e.message}")
            }
        }
    }

    private fun unzipFileStreaming(zipFile: File, targetDir: File) {
        val buffer = ByteArray(8192) // 8KB buffer
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(targetDir, zipEntry.name)

                // Protect against zip slip vulnerability
                val canonicalDestPath = newFile.canonicalPath
                if (!canonicalDestPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target dir: ${zipEntry.name}")
                }

                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    // Extract file with BufferedOutputStream to prevent OOM and speed up I/O
                    BufferedOutputStream(FileOutputStream(newFile)).use { bos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            bos.write(buffer, 0, len)
                        }
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
    }
}
