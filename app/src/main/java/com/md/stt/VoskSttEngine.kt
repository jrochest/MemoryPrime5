package com.md.stt

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class VoskSttEngine(private val context: Context) : SttEngine {

    private var model: Model? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        try {
            val provider = VoskModelProvider(context)
            model = provider.getModel()
            return@withContext model != null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    override suspend fun transcribe(pcmData: ShortArray): TranscriptionResult? = withContext(Dispatchers.Default) {
        val currentModel = model ?: return@withContext null
        try {
            // Vosk Recognizer requires 16000Hz model
            val recognizer = Recognizer(currentModel, 16000f)
            recognizer.setWords(true)

            // Recognizer accepts short array
            val isFinal = recognizer.acceptWaveForm(pcmData, pcmData.size)
            
            val resultJson = if (isFinal) {
                recognizer.result
            } else {
                recognizer.finalResult
            }
            
            recognizer.close()
            
            // Parse {"text": "the recognized sentence"}
            val jsonObject = JSONObject(resultJson)
            val textString = jsonObject.optString("text", "").ifBlank { null } ?: return@withContext null
            
            var confidenceSum = 0f
            var wordCount = 0
            
            // Typical Vosk word format: { "conf": 0.999, "end": 0.42, "start": 0.0, "word": "hello" }
            if (jsonObject.has("result")) {
                val wordsArray = jsonObject.optJSONArray("result")
                if (wordsArray != null) {
                    for (i in 0 until wordsArray.length()) {
                        val wordObj = wordsArray.optJSONObject(i)
                        if (wordObj != null && wordObj.has("conf")) {
                            confidenceSum += wordObj.optDouble("conf", 1.0).toFloat()
                            wordCount++
                        }
                    }
                }
            }
            
            val avgConfidence = if (wordCount > 0) (confidenceSum / wordCount) else 1f
            
            return@withContext TranscriptionResult(text = textString, confidence = avgConfidence)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    override fun release() {
        model?.close()
        model = null
    }
}
