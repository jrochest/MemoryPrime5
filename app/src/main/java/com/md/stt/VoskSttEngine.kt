package com.md.stt

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.coroutineContext

class VoskSttEngine(private val context: Context) : SttEngine {

    private var model: Model? = null
    var modelName: String = "unknown"
        private set

    override suspend fun initialize(useSmallModel: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        try {
            val provider = VoskModelProvider(context)
            if (useSmallModel) {
                Log.i("VoskSttEngine", "Initializing with SMALL (bundled) model")
                model = provider.getSmallModel()
                modelName = "bundled-small"
            } else {
                Log.i("VoskSttEngine", "Initializing with best available model")
                val isHiFi = context.getSharedPreferences("VoskModelPrefs", Context.MODE_PRIVATE)
                    .getBoolean("high_fidelity_model_ready", false)
                model = provider.getModel()
                modelName = if (isHiFi) "gigaspeech" else "bundled-small"
            }
            Log.i("VoskSttEngine", "Model loaded: $modelName")
            return@withContext model != null
        } catch (e: Exception) {
            Log.e("VoskSttEngine", "Failed to initialize", e)
            e.printStackTrace()
            return@withContext false
        }
    }

    override suspend fun transcribe(pcmData: ShortArray): TranscriptionResult? = withContext(Dispatchers.Default) {
        val currentModel = model ?: return@withContext null
        val startTimeMs = System.currentTimeMillis()
        try {
            // Vosk Recognizer requires 16000Hz model
            val recognizer = Recognizer(currentModel, 16000f)
            recognizer.setWords(true)

            // Recognizer accepts short array
            val chunkSize = 4096
            var offset = 0
            var isFinal = false
            var chunksProcessed = 0
            
            Log.d("VoskSttEngine", "Starting transcription of ${pcmData.size} samples (~${pcmData.size / 16000}s of audio)")
            
            while (offset < pcmData.size) {
                yield() // Cooperate with cancellation and prevent blocking single thread
                if (!coroutineContext.isActive) {
                    Log.w("VoskSttEngine", "Transcription cancelled at offset $offset/${pcmData.size}")
                    recognizer.close()
                    return@withContext null
                }
                val remaining = pcmData.size - offset
                val size = if (remaining > chunkSize) chunkSize else remaining
                val chunk = pcmData.copyOfRange(offset, offset + size)
                isFinal = recognizer.acceptWaveForm(chunk, size)
                offset += size
                chunksProcessed++
                
                // Log progress every ~50k samples (~3 seconds of audio)
                if (chunksProcessed % 12 == 0) {
                    val pct = (offset.toFloat() / pcmData.size * 100).toInt()
                    Log.d("VoskSttEngine", "  Transcription progress: $pct% ($offset/${pcmData.size} samples)")
                }
            }
            val resultJson = if (isFinal) {
                recognizer.result
            } else {
                recognizer.finalResult
            }
            
            recognizer.close()
            
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            Log.d("VoskSttEngine", "Transcription completed in ${elapsedMs}ms for ${pcmData.size} samples")
            
            // Parse {"text": "the recognized sentence"}
            val jsonObject = JSONObject(resultJson)
            val rawText = jsonObject.optString("text", "")
            if (rawText.isBlank()) {
                Log.d("VoskSttEngine", "No speech recognized in ${pcmData.size} samples (${elapsedMs}ms)")
                return@withContext TranscriptionResult(text = "", confidence = 0f)
            }
            val textString = rawText
            
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
            
            Log.d("VoskSttEngine", "Result: '$textString' (${wordCount} words, conf=${String.format("%.2f", avgConfidence)}, ${elapsedMs}ms)")
            
            return@withContext TranscriptionResult(text = textString, confidence = avgConfidence)
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            Log.e("VoskSttEngine", "Transcription failed after ${elapsedMs}ms", e)
            e.printStackTrace()
            return@withContext null
        }
    }

    override fun release() {
        model?.close()
        model = null
    }
}
