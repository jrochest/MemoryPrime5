package com.md.stt

// ... existing imports ...
data class TranscriptionResult(
    val text: String,
    val confidence: Float
)

/**
 * Interface definition for an offline Speech-To-Text engine.
 */
interface SttEngine {
    /**
     * Initializes the engine. This may involve loading ML models from disk.
     */
    suspend fun initialize(): Boolean

    /**
     * Transcribes the given 16-bit PCM array into text.
     * @param pcmData 16kHz, 16-bit Mono PCM data.
     * @return The transcription result, or null if recognition failed.
     */
    suspend fun transcribe(pcmData: ShortArray): TranscriptionResult?
    
    /**
     * Releases any resources held by the engine.
     */
    fun release()
}
