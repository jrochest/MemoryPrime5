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

    init {
        // Clean up any previously downloaded high-fidelity models
        cleanupOldModels()
    }

    private fun cleanupOldModels() {
        try {
            // 1. Delete the extracted vosk_models directory
            val baseTargetDir = File(context.filesDir, "vosk_models")
            if (baseTargetDir.exists()) {
                Log.i("VoskModelProvider", "Cleaning up old vosk_models directory")
                baseTargetDir.deleteRecursively()
            }

            // 2. Delete the downloaded zip file
            val zipFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "vosk-model-en-us-0.42-gigaspeech.zip")
            if (zipFile.exists()) {
                Log.i("VoskModelProvider", "Cleaning up old downloaded model zip file")
                zipFile.delete()
            }

            // 3. Clear the shared preferences flag
            val prefs = context.getSharedPreferences("VoskModelPrefs", Context.MODE_PRIVATE)
            if (prefs.contains("high_fidelity_model_ready")) {
                Log.i("VoskModelProvider", "Clearing high_fidelity_model_ready preference")
                prefs.edit().remove("high_fidelity_model_ready").apply()
            }
        } catch (e: Exception) {
            Log.e("VoskModelProvider", "Error cleaning up old models", e)
        }
    }

    suspend fun getModel(): Model? = suspendCancellableCoroutine { continuation ->
        // Bundled asset model
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
