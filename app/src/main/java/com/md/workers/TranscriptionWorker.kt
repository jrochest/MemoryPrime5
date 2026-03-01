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
import kotlinx.coroutines.yield

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioDecoder = AudioDecoder()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("TranscriptionWorker", "Starting transcription batch")
        
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w("TranscriptionWorker", "Could not set foreground info", e)
        }
        
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
            // Find notes missing transcripts that haven't been attempted yet
            val selection = """
                ((${Note.QUESTION} IS NOT NULL AND ${Note.QUESTION} != '') AND ${AbstractNote.QUESTION_TRANSCRIPT} IS NULL AND ${AbstractNote.QUESTION_TRANSCRIPT_ATTEMPTED} = 0)
                OR 
                ((${Note.ANSWER} IS NOT NULL AND ${Note.ANSWER} != '') AND ${AbstractNote.ANSWER_TRANSCRIPT} IS NULL AND ${AbstractNote.ANSWER_TRANSCRIPT_ATTEMPTED} = 0)
            """.trimIndent()

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
                "processedCount" to 0,
                "skippedCount" to 0
            ))
            
            var sessionConfSum = 0f
            var sessionConfCount = 0
            var skippedCount = 0

            while (isActive && !isStopped && cursor.moveToNext() && processedCount < maxBatchSize) {
                yield()
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(Note._ID))
                val question = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION))
                val answer = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER))
                var questionTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION_TRANSCRIPT))
                var answerTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER_TRANSCRIPT))

                var questionTranscriptConf = 0f
                var answerTranscriptConf = 0f
                var updated = false
                val now = System.currentTimeMillis()

                // Process Question
                var questionAttemptedTime = 0L
                if (!question.isNullOrBlank() && questionTranscript.isNullOrBlank()) {
                    val path = AudioPlayer.sanitizePath(question)
                    Log.d("TranscriptionWorker", "Note ID $id: Attempting to decode Question audio from '$path'")
                    val pcm = audioDecoder.decodeToPcm(path)
                    if (pcm != null) {
                        Log.d("TranscriptionWorker", "Note ID $id: Question decoded successfully (${pcm.size} samples). Transcribing...")
                        val transcriptResult = sttEngine.transcribe(pcm)
                        if (transcriptResult != null && !transcriptResult.text.isBlank()) {
                            Log.d("TranscriptionWorker", "Note ID $id: Question transcribed -> '${transcriptResult.text}'")
                            questionTranscript = transcriptResult.text
                            questionTranscriptConf = transcriptResult.confidence
                            updated = true
                        } else {
                            Log.w("TranscriptionWorker", "Note ID $id: Question transcription returned null or blank text.")
                            skippedCount++
                        }
                    } else {
                        Log.w("TranscriptionWorker", "Note ID $id: Question audio decoding failed for '$path'.")
                        skippedCount++
                    }
                    questionAttemptedTime = now
                }

                // Process Answer
                var answerAttemptedTime = 0L
                if (!answer.isNullOrBlank() && answerTranscript.isNullOrBlank()) {
                    val path = AudioPlayer.sanitizePath(answer)
                    Log.d("TranscriptionWorker", "Note ID $id: Attempting to decode Answer audio from '$path'")
                    val pcm = audioDecoder.decodeToPcm(path)
                    if (pcm != null) {
                        Log.d("TranscriptionWorker", "Note ID $id: Answer decoded successfully (${pcm.size} samples). Transcribing...")
                        val transcriptResult = sttEngine.transcribe(pcm)
                        if (transcriptResult != null && !transcriptResult.text.isBlank()) {
                            Log.d("TranscriptionWorker", "Note ID $id: Answer transcribed -> '${transcriptResult.text}'")
                            answerTranscript = transcriptResult.text
                            answerTranscriptConf = transcriptResult.confidence
                            updated = true
                        } else {
                            Log.w("TranscriptionWorker", "Note ID $id: Answer transcription returned null or blank text.")
                            skippedCount++
                        }
                    } else {
                        Log.w("TranscriptionWorker", "Note ID $id: Answer audio decoding failed for '$path'.")
                        skippedCount++
                    }
                    answerAttemptedTime = now
                }

                if (updated || questionAttemptedTime > 0L || answerAttemptedTime > 0L) {
                    val values = ContentValues().apply {
                        if (updated) {
                            put(AbstractNote.QUESTION_TRANSCRIPT, questionTranscript)
                            put(AbstractNote.ANSWER_TRANSCRIPT, answerTranscript)
                            if (questionTranscriptConf > 0) {
                                put(AbstractNote.QUESTION_TRANSCRIPT_CONFIDENCE, questionTranscriptConf)
                            }
                            if (answerTranscriptConf > 0) {
                                put(AbstractNote.ANSWER_TRANSCRIPT_CONFIDENCE, answerTranscriptConf)
                            }
                        }
                        if (questionAttemptedTime > 0L) {
                            put(AbstractNote.QUESTION_TRANSCRIPT_ATTEMPTED, questionAttemptedTime)
                        }
                        if (answerAttemptedTime > 0L) {
                            put(AbstractNote.ANSWER_TRANSCRIPT_ATTEMPTED, answerAttemptedTime)
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
                        Log.i("TranscriptionWorker", "Progress Update: Processed $processedCount notes ($skippedCount skipped). Current Average Confidence: ${"%.2f".format(avg * 100)}%")
                        
                        setProgress(androidx.work.workDataOf(
                            "statusMessage" to "Transcribing... ($processedCount / $totalCount)\nSkipped: $skippedCount",
                            "totalCount" to totalCount,
                            "processedCount" to processedCount,
                            "skippedCount" to skippedCount,
                            "averageConfidence" to avg
                        ))
                    }
                }
            }

            cursor.close()
            sttEngine.release()
            
            if (isStopped || !isActive) {
                Log.d("TranscriptionWorker", "Transcription batch cancelled. Processed: $processedCount, Skipped: $skippedCount")
                return@withContext Result.success()
            }
            
            setProgress(androidx.work.workDataOf(
                "statusMessage" to "Finished batch. Processed: $processedCount notes, Skipped: $skippedCount.",
                "totalCount" to totalCount,
                "processedCount" to processedCount,
                "skippedCount" to skippedCount
            ))

            Log.d("TranscriptionWorker", "Finished transcription batch. Processed: $processedCount, Skipped: $skippedCount")
            return@withContext Result.success()
            
        } catch (e: Exception) {
            Log.e("TranscriptionWorker", "Worker exception", e)
            e.printStackTrace()
            sttEngine.release()
            return@withContext Result.retry()
        }
    }

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val title = "Transcribing Audio"
        val cancel = "Cancel"
        
        val intent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "transcription_channel",
                "Background Transcription",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "transcription_channel")
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("Processing audio notes...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(1992, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(1992, notification)
        }
    }
}
