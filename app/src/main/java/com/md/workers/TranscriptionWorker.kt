package com.md.workers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.md.AudioPlayer
import com.md.audio.AudioDecoder
import com.md.provider.AbstractNote
import com.md.provider.Note
import com.md.stt.SttEngine
import com.md.stt.VoskSttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioDecoder = AudioDecoder()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("TranscriptionWorker", "Starting transcription batch")
        
        setProgress(androidx.work.workDataOf(
            "statusMessage" to "Initializing Speech-to-Text Engine..."
        ))

        val sttEngine: SttEngine = VoskSttEngine(applicationContext)
        val initialized = sttEngine.initialize()
        if (!initialized) {
            Log.e("TranscriptionWorker", "Failed to initialize STT Engine")
            // A failure here often means the model assets are missing
            return@withContext Result.failure()
        }

        try {
            // Find notes missing transcripts
            val selection = "(${AbstractNote.QUESTION_TRANSCRIPT} IS NULL OR ${AbstractNote.ANSWER_TRANSCRIPT} IS NULL)"

            val cursor = applicationContext.contentResolver.query(
                AbstractNote.CONTENT_URI,
                null,
                selection,
                null,
                "${Note._ID} DESC" // Process newest first
            )

            if (cursor == null) {
                sttEngine.release()
                return@withContext Result.success()
            }

            val totalCount = cursor.count
            var processedCount = 0
            val maxBatchSize = 1000 // Process up to 1000 at a time to prevent timeout
            
            setProgress(androidx.work.workDataOf(
                "statusMessage" to "Found $totalCount notes needing transcription.",
                "totalCount" to totalCount,
                "processedCount" to 0
            ))
            
            var sessionConfSum = 0f
            var sessionConfCount = 0

            while (isActive && !isStopped && cursor.moveToNext() && processedCount < maxBatchSize) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(Note._ID))
                val question = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION))
                val answer = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER))
                var questionTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION_TRANSCRIPT))
                var answerTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER_TRANSCRIPT))

                var questionTranscriptConf = 0f
                var answerTranscriptConf = 0f

                var updated = false

                // Process Question
                if (!question.isNullOrBlank() && questionTranscript.isNullOrBlank()) {
                    val path = AudioPlayer.sanitizePath(question)
                    val pcm = audioDecoder.decodeToPcm(path)
                    if (pcm != null) {
                        val transcriptResult = sttEngine.transcribe(pcm)
                        if (transcriptResult != null && !transcriptResult.text.isBlank()) {
                            questionTranscript = transcriptResult.text
                            questionTranscriptConf = transcriptResult.confidence
                            updated = true
                        }
                    }
                }

                // Process Answer
                if (!answer.isNullOrBlank() && answerTranscript.isNullOrBlank()) {
                    val path = AudioPlayer.sanitizePath(answer)
                    val pcm = audioDecoder.decodeToPcm(path)
                    if (pcm != null) {
                        val transcriptResult = sttEngine.transcribe(pcm)
                        if (transcriptResult != null && !transcriptResult.text.isBlank()) {
                            answerTranscript = transcriptResult.text
                            answerTranscriptConf = transcriptResult.confidence
                            updated = true
                        }
                    }
                }

                if (updated) {
                    val values = ContentValues().apply {
                        put(AbstractNote.QUESTION_TRANSCRIPT, questionTranscript)
                        put(AbstractNote.ANSWER_TRANSCRIPT, answerTranscript)
                        if (questionTranscriptConf > 0) {
                            put(AbstractNote.QUESTION_TRANSCRIPT_CONFIDENCE, questionTranscriptConf)
                        }
                        if (answerTranscriptConf > 0) {
                            put(AbstractNote.ANSWER_TRANSCRIPT_CONFIDENCE, answerTranscriptConf)
                        }
                    }
                    applicationContext.contentResolver.update(
                        AbstractNote.CONTENT_URI,
                        values,
                        "${Note._ID} = ?",
                        arrayOf(id.toString())
                    )
                    processedCount++
                    
                    if (questionTranscriptConf > 0) { sessionConfSum += questionTranscriptConf; sessionConfCount++ }
                    if (answerTranscriptConf > 0) { sessionConfSum += answerTranscriptConf; sessionConfCount++ }
                    
                    if (processedCount % 1 == 0) {
                        val avg = if (sessionConfCount > 0) sessionConfSum / sessionConfCount else 0f
                        Log.i("TranscriptionWorker", "Progress Update: Transcribed $processedCount notes. Current Average Confidence: ${"%.2f".format(avg * 100)}%")
                        
                        setProgress(androidx.work.workDataOf(
                            "statusMessage" to "Transcribing... ($processedCount / $totalCount)",
                            "totalCount" to totalCount,
                            "processedCount" to processedCount,
                            "averageConfidence" to avg
                        ))
                    }
                }
            }

            cursor.close()
            sttEngine.release()
            
            if (isStopped || !isActive) {
                Log.d("TranscriptionWorker", "Transcription batch cancelled. Processed: $processedCount")
                return@withContext Result.success()
            }
            
            setProgress(androidx.work.workDataOf(
                "statusMessage" to "Finished transcription batch. Processed: $processedCount notes.",
                "totalCount" to totalCount,
                "processedCount" to processedCount
            ))

            Log.d("TranscriptionWorker", "Finished transcription batch. Processed: $processedCount")
            return@withContext Result.success()
            
        } catch (e: Exception) {
            e.printStackTrace()
            sttEngine.release()
            return@withContext Result.retry()
        }
    }
}
