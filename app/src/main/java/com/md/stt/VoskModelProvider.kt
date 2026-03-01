package com.md.stt

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class VoskModelProvider(private val context: Context) {

    private val downloadManager = ModelDownloadManager(context)

    fun startHighFidelityDownload() {
        downloadManager.startDownload()
    }

    suspend fun getModel(): Model? = suspendCancellableCoroutine { continuation ->
        if (downloadManager.isHighFidelityModelReady()) {
            try {
                // The extracted folder is placed in filesDir/vosk_models
                // and internally contains another folder with the model name.
                val baseTargetDir = File(context.filesDir, "vosk_models")
                val targetDir = File(baseTargetDir, ModelDownloadManager.MODEL_DIR_NAME)
                
                if (targetDir.exists()) {
                    Log.i("VoskModelProvider", "Loading high-fidelity downloaded model from ${targetDir.absolutePath}")
                    val model = Model(targetDir.absolutePath)
                    continuation.resume(model)
                    return@suspendCancellableCoroutine
                } else {
                    Log.w("VoskModelProvider", "High-fidelity model flagged as ready but directory missing. Falling back.")
                }
            } catch (e: Exception) {
                Log.e("VoskModelProvider", "Failed to load high-fidelity model", e)
            }
        }

        // Fallback or default model (bundled asset)
        Log.i("VoskModelProvider", "Unpacking bundled asset model")
        StorageService.unpack(context, "model", "model",
            { model: Model ->
                Log.i("VoskModelProvider", "Successfully unpacked bundled model")
                continuation.resume(model)
            },
            { exception: java.io.IOException ->
                Log.e("VoskModelProvider", "Failed to unpack bundled model", exception)
                continuation.resumeWithException(exception)
            }
        )
    }
}
